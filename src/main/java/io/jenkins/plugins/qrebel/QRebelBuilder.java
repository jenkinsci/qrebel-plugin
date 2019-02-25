package io.jenkins.plugins.qrebel;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.VariableResolver;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 *
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

public class QRebelBuilder extends Builder implements SimpleBuildStep {
    private static final String QREBEL_BASE_URL = "https://hub.qrebel.com/api/applications/";

    private final String appName;
    private final String targetBuild;
    private final String baselineBuild;
    private final String apiKey;
    public int durationFail;
    public int ioFail;
    public int exceptionFail;
    public int threshold;

    private VariableResolver buildVariableResolver;
    private PrintStream logger;
    private EnvVars envVars;

    @DataBoundConstructor
    public QRebelBuilder(String appName, String targetBuild, String baselineBuild, String apiKey, int durationFail,
                         int ioFail, int exceptionFail, int threshold) {
        this.appName = appName;
        this.targetBuild = targetBuild;
        this.baselineBuild = baselineBuild;
        this.apiKey = apiKey;
        this.durationFail = durationFail;
        this.ioFail = ioFail;
        this.exceptionFail = exceptionFail;
        this.threshold = threshold;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAppName() {
        return appName;
    }

    public String getTargetBuild() {
        return targetBuild;
    }

    public String getBaselineBuild() {
        return baselineBuild;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        buildVariableResolver = build.getBuildVariableResolver();

        try {
            envVars = build.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return super.prebuild(build, listener);
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException {
        logger = listener.getLogger();
        process(logger, run);
    }

    @Symbol("qrebel")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.QRebelBuilder_DescriptorImpl_DisplayName();
        }
    }

    private String resolveParameter(String name) {
        if (!name.startsWith("$")) {
            return name;
        }

        Optional<Object> resolvedParameter = Optional.ofNullable(buildVariableResolver.resolve(name.substring(1)));

        if (resolvedParameter.isPresent()) {
            String resolvedValue = (String) resolvedParameter.get();
            if (resolvedValue.contains("$")) {
                //TODO Get the variable and query the value
                return resolveParameter(resolvedValue);
            } else {
                return (String) resolvedParameter.get();
            }
        }
        if (envVars.get(name) != null) {
            logger.println("Falling back to envVars " + envVars.get(name));
            return envVars.get(name);
        }
        if (System.getenv(name) != null) {
            logger.println("Falling back to System.getEnv " + System.getenv(name));
            return System.getenv(name);
        }

        throw new IllegalArgumentException(String.format("Parameter %s is not set", name));
    }

    private void process(PrintStream logger, Run<?, ?> run) throws IOException {
        logger.println("AppName" + appName + " resolveAppName " + resolveParameter(appName));
        logger.println("Baseline Build: " + baselineBuild);
        logger.println("Target Build: " + targetBuild + " resolved: " + resolveParameter(targetBuild));

        setRemoteBaseline();

        StringBuilder issues = getIssuesJson();
        QRebelData qRData = QRebelData.parse(issues.toString());

        boolean failBuild = false;

        if (qRData.getDurationCount() > durationFail
                || qRData.getIOCount() > ioFail
                || qRData.getExceptionCount() > exceptionFail
                || isThresholdProvidedAndExceeded(qRData)) {
            failBuild = true;
        }

        if (failBuild) {
            run.setResult(Result.FAILURE);
            logFailDescription(run, logger, qRData);
        }
    }

    private boolean isThresholdProvidedAndExceeded(QRebelData qRData) {
        Optional<List<Long>> entryPointTimes = qRData.getEntryPointTimes();
        if (!entryPointTimes.isPresent()) {
            return false;
        }

        OptionalLong maxDelayTime = entryPointTimes.get().stream().mapToLong(v -> v).max();
        if (maxDelayTime.isPresent()) {
            return threshold > 0 && threshold <= (int) maxDelayTime.getAsLong();
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
        con.setRequestProperty("authorization", apiKey);

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
        URL baseLineUrl = new URL("https://hub.xrebel.com/api/applications/" + resolveParameter(appName) + "/baselines/default/");
        HttpURLConnection baseUrl = (HttpURLConnection) baseLineUrl.openConnection();
        baseUrl.setRequestMethod("PUT");
        baseUrl.setDoOutput(true);
        baseUrl.setRequestProperty("Content-Type", "application/json");
        baseUrl.setRequestProperty("authorization", apiKey);
        OutputStreamWriter out = new OutputStreamWriter(baseUrl.getOutputStream(), StandardCharsets.UTF_8);

        if (StringUtils.isNotEmpty(baselineBuild)) {
            out.write("{ \"build\": \"" + baselineBuild + "\" }");
        }

        out.close();
        baseUrl.getInputStream();
    }

    private String buildApiUrl() {
        return QREBEL_BASE_URL +
                resolveParameter(appName) +
                "/" +
                "issues" +
                "/?targetBuild=" +
                resolveParameter(targetBuild) +
                "&defaultBaseline";
    }

    private void logFailDescription(Run<?, ?> run, PrintStream logger, QRebelData qRData) throws IOException {
        StringBuilder descriptionBuilder = new StringBuilder();
        if (run.getDescription() != null && run.getDescription().length() > 0) {
            descriptionBuilder.append(run.getDescription());
        }
        descriptionBuilder.append(String.format("Failing build due to performance regressions found in %s compared to %s. <br/>" +
                        "Slow Requests: %d <br/>" +
                        "Excessive IO: %d <br/>" +
                        "Exceptions: %d <br/>" +
                        "Threshold limit(ms): %d ms | slowest endpoint time(ms): %d ms <br/>" +
                        "For full report check your <a href= %s >dashboard</a>.<br/>",
                qRData.getAppName(), resolveParameter(baselineBuild), qRData.getDurationCount(), qRData.getIOCount(),
                qRData.getExceptionCount(), threshold, maximumDelay(qRData), qRData.getViewUrl()));
        run.setDescription(descriptionBuilder.toString());

        logger.println("Performance regression have been found in the current build. Failing build.");

        logger.println(String.format("Slow Requests: %d%n" +
                        " Excessive IO: %d %n" +
                        " Exceptions: %d  %n" +
                        " Threshold limit(ms): %d ms | slowest endpoint time(ms): %d ms",
                qRData.getDurationCount(), qRData.getIOCount(), qRData.getExceptionCount(), threshold, maximumDelay(qRData)));

        logger.println("For more detail check your <a href=\"+ qRData.getViewUrl()/\">dashboard</a>");
    }
}
