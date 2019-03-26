/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.plugins.envinjectapi.util.EnvVarsResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.IssuesResponse;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.IssuesRequest;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.QRebelRestApi;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.QRebelRestApiClient;

import feign.FeignException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * A Jenkins build will be marked as failed if there are some issues detected by QRebel
 * The issues are obtained via HTTP requests to the QRebel server. Some of them can be ignored depending on the plugin configuration.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(onConstructor = @__({@DataBoundConstructor}))
public class QRebelPublisher extends Recorder implements SimpleBuildStep {

  static final String PLUGIN_SHORT_NAME = "qrebel";

  final String appName;
  final String targetBuild;
  final String targetVersion;
  final String baselineBuild;
  final String baselineVersion;
  final String apiToken;
  final String serverUrl;
  final String comparisonStrategy;
  final long slowRequestsAllowed;
  final long excessiveIoAllowed;
  final long exceptionsAllowed;
  final long slaGlobalLimit;
  final boolean DURATION;
  final boolean IO;
  final boolean EXCEPTIONS;


  @Symbol(PLUGIN_SHORT_NAME)
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public @Nonnull
    String getDisplayName() {
      return "Monitor performance regression with QRebel";
    }

    public FormValidation doTestConnection(@QueryParameter("appName") final String appName,
                                           @QueryParameter("apiToken") final String apiToken,
                                           @QueryParameter("serverUrl") final String serverUrl) {
      if (StringUtils.isBlank(appName) || StringUtils.isBlank(apiToken) || StringUtils.isBlank(serverUrl)) {
        return FormValidation.error("Connection parameters cannot be blank");
      }
      try {
        QRebelRestApiClient.createBasic(serverUrl).testConnection(apiToken, appName);
        return FormValidation.ok("Success");
      }
      catch (FeignException e) {
        switch (e.status()) {
          case 401: return FormValidation.error("Authorization failed");
          case 404: return FormValidation.error("No application found");
        }
        Throwable cause = e.getCause();
        if (cause != null && StringUtils.isNotBlank(cause.toString())) {
          return FormValidation.error(cause.toString());
        }
        return FormValidation.error(e.getMessage());
      }
    }

    public FormValidation doCheckBlank(@QueryParameter String value) {
      return StringUtils.isBlank(value) ? FormValidation.error("Mandatory field") : FormValidation.ok();
    }
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException {
    PrintStream logger = listener.getLogger();
    Fields fields = resolveFields(run);

    logger.println("AppName: " + fields.appName);
    logger.println("Baseline Build: " + fields.baselineBuild);
    logger.println("Baseline Version: " + fields.baselineVersion);
    logger.println("Target Build: " + fields.targetBuild);
    logger.println("Target Version: " + fields.targetVersion);
    validateMinimalMandatoryParameters(fields);

    IssuesResponse qRData = getIssues(fields, logger);
    IssuesStats stats = new IssuesStats(qRData);

    boolean failBuild = qRData.issuesCount.DURATION > fields.slowRequestsAllowed
        || qRData.issuesCount.IO > fields.excessiveIoAllowed
        || qRData.issuesCount.EXCEPTIONS > fields.exceptionsAllowed
        || stats.isSlaGlobalLimitExceeded(fields.slaGlobalLimit);

    if (failBuild) {
      run.setResult(Result.FAILURE);
      String failureDescription = getFailureDescription(qRData, fields, stats.getSlowestDuration());
      String initialDescription = run.getDescription();
      run.setDescription(StringUtils.isEmpty(initialDescription) ? failureDescription : initialDescription + "<br/>" + failureDescription);
      logger.println(failureDescription);
    }
  }

  //  fails a build and add error message to the log if the minimal param set in undefined
  private static void validateMinimalMandatoryParameters(Fields fields) {
    if (StringUtils.isEmpty(fields.appName) || StringUtils.isEmpty(fields.serverUrl) || StringUtils.isEmpty(fields.apiToken)) {
      throw new IllegalArgumentException("Connection parameters cannot be blank");
    }
    if (StringUtils.isEmpty(fields.targetBuild)) {
      throw new IllegalArgumentException("Target build name cannot be blank");
    }
    if (StringUtils.isEmpty(fields.baselineBuild) && fields.comparisonStrategy == ComparisonStrategy.BASELINE) {
      throw new IllegalArgumentException("Baseline build name cannot be blank");
    }
  }

  // Get issues via REST
  private static IssuesResponse getIssues(Fields fields, PrintStream logger) {
    QRebelRestApi restApi = QRebelRestApiClient.create(fields.serverUrl, logger);
    IssuesRequest.IssuesRequestBuilder requestBuilder = IssuesRequest.builder()
        .targetBuild(fields.targetBuild)
        .targetVersion(fields.targetVersion)
        .slowRequestsAllowed(fields.slowRequestsAllowed)
        .excessiveIOAllowed(fields.excessiveIoAllowed)
        .exceptionsAllowed(fields.exceptionsAllowed)
        .jenkinsPluginVersion(PluginVersion.get())
        .issues(fields.issueTypes);
    if (ComparisonStrategy.BASELINE.equals(fields.comparisonStrategy)) {
      requestBuilder = requestBuilder
          .baselineBuild(fields.baselineBuild)
          .baselineVersion(fields.baselineVersion);
    }
    else if (ComparisonStrategy.DEFAULT_BASELINE.equals(fields.comparisonStrategy)) {
      requestBuilder = requestBuilder.defaultBaseline(true);
    }
    return restApi.getIssues(fields.apiToken, fields.appName, requestBuilder.build());
  }

  private String toIssueTypes() {
    List<String> issueTypes = new ArrayList<>();
    if (DURATION) {
      issueTypes.add(IssueType.DURATION.name());
    }
    if (IO) {
      issueTypes.add(IssueType.IO.name());
    }
    if (EXCEPTIONS) {
      issueTypes.add(IssueType.EXCEPTIONS.name());
    }
    return StringUtils.join(issueTypes, ",");
  }

  private Fields resolveFields(Run<?, ?> run) {
    return Fields.builder()
        .apiToken(resolveEnvVarFromRun(apiToken, run))
        .appName(resolveEnvVarFromRun(appName, run))
        .baselineBuild(resolveEnvVarFromRun(baselineBuild, run))
        .baselineVersion(resolveEnvVarFromRun(baselineVersion, run))
        .targetBuild(resolveEnvVarFromRun(targetBuild, run))
        .targetVersion(resolveEnvVarFromRun(targetVersion, run))
        .serverUrl(resolveEnvVarFromRun(serverUrl, run))
        .slowRequestsAllowed(slowRequestsAllowed)
        .exceptionsAllowed(exceptionsAllowed)
        .excessiveIoAllowed(excessiveIoAllowed)
        .slaGlobalLimit(slaGlobalLimit)
        .comparisonStrategy(ComparisonStrategy.valueOf(comparisonStrategy))
        .issueTypes(toIssueTypes())
        .build();
  }

  // resolve fields
  private static String resolveEnvVarFromRun(String value, Run<?, ?> run) {
    try {
      return StringUtils.trimToNull(EnvVarsResolver.resolveEnvVars(run, value));
    }
    catch (EnvInjectException e) {
      throw new IllegalStateException("Unable to get Env Variable " + value, e);
    }
  }

  // describe failure reason
  private static String getFailureDescription(IssuesResponse qRData, Fields fields, long slowestDuration) {
    return String.format("Failing build due to performance regressions found in %s compared to build %s version %s. <br/>%n" +
            "Slow Requests: %d <br/>%n" +
            "Excessive IO: %d <br/>%n" +
            "Exceptions: %d <br/>%n" +
            "SLA global limit (ms): %d ms | slowest endpoint time(ms): %d ms <br/>%n" +
            "For full report check your <a href= %s >dashboard</a>.<br/>%n",
        qRData.appName, fields.baselineBuild, fields.baselineVersion, qRData.issuesCount.DURATION,
        qRData.issuesCount.IO, qRData.issuesCount.EXCEPTIONS,
        fields.slaGlobalLimit, slowestDuration, qRData.appViewUrl);
  }

  // Helper method for the jelly view to determine comparisonStrategy
  public String isStrategy(String comparisonStrategy) {
    return StringUtils.equalsIgnoreCase(comparisonStrategy, this.comparisonStrategy) ? "true" : "";
  }
}
