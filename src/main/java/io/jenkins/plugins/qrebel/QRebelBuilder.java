package io.jenkins.plugins.qrebel;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.VariableResolver;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

public class QRebelBuilder extends Builder implements SimpleBuildStep {
    private static final String QREBEL_BASE_URL = "https://hub.qrebel.com/api/applications/";

    private final String appName;
    private final String target;
    private final String baseline;
    private final String apiKey;
    private String entryPoints;
    public int durationFail;
    public int ioFail;
    public int exceptionFail;

    private VariableResolver buildVariableResolver;

    @DataBoundConstructor
    public QRebelBuilder(String appName, String target, String baseline, String entryPoints, String apiKey,
                         int durationFail, int ioFail, int exceptionFail) {
        this.appName = appName;
        this.target = target;
        this.baseline = baseline;
        this.apiKey = apiKey;
        this.durationFail = durationFail;
        this.ioFail = ioFail;
        this.exceptionFail = exceptionFail;
        this.entryPoints = entryPoints;
    }

    public String getEntryPoints() {
        return entryPoints;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAppName() {
        return appName;
    }

    public String getTarget() {
        return target;
    }

    public String getBaseline() {
        return baseline;
    }

    @DataBoundSetter
    public void setEntryPoints(String entryPoints) {
        this.entryPoints = entryPoints;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        buildVariableResolver = build.getBuildVariableResolver();
        return super.prebuild(build, listener);
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException {
        process(listener.getLogger(), run);
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
            return (String) resolvedParameter.get();
        }

        throw new IllegalArgumentException(String.format("Parameter %s is not set", name));
    }

    private void process(PrintStream logger, Run<?, ?> run) throws IOException {
        setRemoteBaseline();

        StringBuilder issues = getIssuesJson(logger);
        QRebelData qRData = QRebelData.parse(issues.toString());

        boolean failBuild = false;
        boolean issuesFail = false;
        boolean entryPointsFail = false;

        if (qRData.getDurationCount() > durationFail
                || qRData.getIOCount() > ioFail
                || qRData.getExceptionCount() > exceptionFail) {
            failBuild = true;
            issuesFail = true;
        }

        if (!entryPoints.isEmpty()) {
            entryPointsFail = hasEntryPointIssues(qRData);
            if (entryPointsFail) {
                failBuild = true;
            }
        }

        if (failBuild) {
            run.setResult(Result.FAILURE);
            logFailDescription(logger, qRData, issuesFail, entryPointsFail);
        }
    }

    private StringBuilder getIssuesJson(PrintStream logger) throws IOException {
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
        OutputStreamWriter out = new OutputStreamWriter(
                baseUrl.getOutputStream(), StandardCharsets.UTF_8);
        out.write("{ \"build\": \"" + baseline + "\" }");
        out.close();
        baseUrl.getInputStream();
    }

    private String buildApiUrl() {
        return QREBEL_BASE_URL +
                resolveParameter(appName) +
                "/" +
                "issues" +
                "/?targetBuild=" +
                resolveParameter(target) +
                "&defaultBaseline";
    }

    private void logFailDescription(PrintStream logger, QRebelData qRData, boolean issuesFail, boolean entryPointsFail) {
        logger.println("Issues has been found in your application. Failing the build.");

        if (issuesFail) {
            logger.println(String.format("Slow Request count is %d, Excessive IO count is %d, Exception count is %d",
                    qRData.getDurationCount(), qRData.getIOCount(), qRData.getExceptionCount()));
        }

        if (entryPointsFail && qRData.getEntryPointNames().isPresent()) {
            logger.println("Following entry points has issues: " + qRData.getEntryPointNames().get());
        }
    }

    private boolean hasEntryPointIssues(QRebelData qRData) {
        Optional<List<String>> entryPointNames = qRData.getEntryPointNames();
        String entryPointsNoComma = entryPoints.replace(",", "");
        String[] entryPointsParsed = StringUtils.substringsBetween(entryPointsNoComma, "\"", "\"");

        return entryPointNames.map(strings -> strings.stream().anyMatch(e -> Arrays.asList(entryPointsParsed)
                .contains(e)))
                .orElse(false);
    }
}
