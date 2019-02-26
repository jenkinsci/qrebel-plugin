package io.jenkins.plugins.qrebel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.commons.lang.StringUtils;

import hudson.model.Build;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 *
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

class QRebelStepPerformer {
    private static final String QREBEL_BASE_URL = "https://hub.qrebel.com/api/applications/";

    private final QRebelBuilder fields;
    private final PrintStream logger;
    private final ParameterResolver resolver;

    private QRebelStepPerformer(QRebelBuilder fields, PrintStream logger, ParameterResolver resolver) {
        this.fields = fields;
        this.logger = logger;
        this.resolver = resolver;
    }

    static void perform(QRebelBuilder stepFields, Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
        if (run instanceof Build) {
            new QRebelStepPerformer(stepFields, listener.getLogger(), ParameterResolver.make((Build) run, listener))
                .perform(run);
        }
    }

    private void perform(Run<?, ?> run) throws IOException {
        logger.println("AppName" + fields.appName + " resolveAppName " + resolver.get(fields.appName));
        logger.println("Baseline Build: " + fields.baselineBuild);
        logger.println("Target Build: " + fields.targetBuild + " resolved: " + resolver.get(fields.targetBuild));

        setRemoteBaseline();

        StringBuilder issues = getIssuesJson();
        QRebelData qRData = QRebelData.parse(issues.toString());

        boolean failBuild = false;

        if (qRData.getDurationCount() > fields.durationFail
                || qRData.getIOCount() > fields.ioFail
                || qRData.getExceptionCount() > fields.exceptionFail
                || isThresholdProvidedAndExceeded(qRData)) {
            failBuild = true;
        }

        if (failBuild) {
            run.setResult(Result.FAILURE);
            String failureDescription = getFailureDescription(qRData, run.getDescription());
            run.setDescription(failureDescription);
            logger.println("Performance regression have been found in the current build. Failing build.");

            logger.println(String.format("Slow Requests: %d%n" +
                    " Excessive IO: %d %n" +
                    " Exceptions: %d  %n" +
                    " Threshold limit(ms): %d ms | slowest endpoint time(ms): %d ms",
                qRData.getDurationCount(), qRData.getIOCount(), qRData.getExceptionCount(), fields.threshold, maximumDelay(qRData)));

            logger.println("For more detail check your <a href=\"+ qRData.getViewUrl()/\">dashboard</a>");
        }
    }

    private boolean isThresholdProvidedAndExceeded(QRebelData qRData) {
        Optional<List<Long>> entryPointTimes = qRData.getEntryPointTimes();
        if (!entryPointTimes.isPresent()) {
            return false;
        }

        OptionalLong maxDelayTime = entryPointTimes.get().stream().mapToLong(v -> v).max();
        if (maxDelayTime.isPresent()) {
            return fields.threshold > 0 && fields.threshold <= (int) maxDelayTime.getAsLong();
        }
        return false;
    }

    private int maximumDelay(QRebelData qRData) {
        Optional<List<Long>> entryPointTimes = qRData.getEntryPointTimes();
        if (!entryPointTimes.isPresent()) {
            return 0;
        }

        OptionalLong maxDelayTime = entryPointTimes.get().stream().mapToLong(v -> v).max();
        if (maxDelayTime.isPresent()) {
            return (int) maxDelayTime.getAsLong();
        }
        return 0;
    }

    private StringBuilder getIssuesJson() throws IOException {
        logger.println("Going to perform QRebel API call..");

        URL issuesUrl = new URL(buildApiUrl());
        HttpURLConnection con = (HttpURLConnection) issuesUrl.openConnection();
        logger.println("Calling URL - " + issuesUrl.toString());

        con.setRequestMethod("GET");
        con.setRequestProperty("authorization", fields.apiKey);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response;
    }

    private void setRemoteBaseline() throws IOException {
        URL baseLineUrl = new URL("https://hub.xrebel.com/api/applications/" + resolver.get(fields.appName) + "/baselines/default/");
        HttpURLConnection baseUrl = (HttpURLConnection) baseLineUrl.openConnection();
        baseUrl.setRequestMethod("PUT");
        baseUrl.setDoOutput(true);
        baseUrl.setRequestProperty("Content-Type", "application/json");
        baseUrl.setRequestProperty("authorization", fields.apiKey);
        OutputStreamWriter out = new OutputStreamWriter(baseUrl.getOutputStream(), StandardCharsets.UTF_8);

        if (StringUtils.isNotEmpty(fields.baselineBuild)) {
            out.write("{ \"build\": \"" + fields.baselineBuild + "\" }");
        }

        out.close();
        baseUrl.getInputStream();
    }

    private String buildApiUrl() {
        return QREBEL_BASE_URL +
                resolver.get(fields.appName) +
                "/" +
                "issues" +
                "/?targetBuild=" +
                resolver.get(fields.targetBuild) +
                "&defaultBaseline";
    }

    private String getFailureDescription(QRebelData qRData, String buildDescription) {
        StringBuilder descriptionBuilder = new StringBuilder();
        if (buildDescription != null && buildDescription.length() > 0) {
            descriptionBuilder.append(buildDescription);
        }
        descriptionBuilder.append(String.format("Failing build due to performance regressions found in %s compared to %s. <br/>" +
                        "Slow Requests: %d <br/>" +
                        "Excessive IO: %d <br/>" +
                        "Exceptions: %d <br/>" +
                        "Threshold limit(ms): %d ms | slowest endpoint time(ms): %d ms <br/>" +
                        "For full report check your <a href= %s >dashboard</a>.<br/>",
                qRData.getAppName(), resolver.get(fields.baselineBuild), qRData.getDurationCount(), qRData.getIOCount(),
                qRData.getExceptionCount(), fields.threshold, maximumDelay(qRData), qRData.getViewUrl()));

        return descriptionBuilder.toString();
    }
}
