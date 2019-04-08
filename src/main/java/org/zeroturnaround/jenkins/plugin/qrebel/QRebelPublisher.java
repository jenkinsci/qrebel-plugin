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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.IssuesResponse;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.IssuesRequest;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.QRebelRestApi;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.QRebelRestApiClient;

import feign.FeignException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import jenkins.tasks.SimpleBuildStep;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Wither;

/**
 * A Jenkins build will be marked as failed if there are some issues detected by QRebel
 * The issues are obtained via HTTP requests to the QRebel server. Some of them can be ignored depending on the plugin configuration.
 */
@Data
@Wither
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
  final String apiUrl;
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

    public final boolean showHidden = Boolean.getBoolean("qrebel.jenkins.showHidden");

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public @Nonnull
    String getDisplayName() {
      return "Monitor performance regressions with QRebel";
    }

    @POST
    public FormValidation doTestConnection(@QueryParameter("appName") final String appName,
                                           @QueryParameter("apiToken") final String apiToken,
                                           @QueryParameter("apiUrl") final String apiUrl) {
      if (StringUtils.isBlank(appName) || StringUtils.isBlank(apiToken) || StringUtils.isBlank(apiUrl)) {
        return FormValidation.error("Connection parameters cannot be blank");
      }
      if (StringUtils.contains(appName, '$') || StringUtils.contains(apiToken, '$') || StringUtils.contains(apiUrl, '$')) {
        return FormValidation.warning("Cannot verify connection containing placeholders ${PLACEHOLDER}");
      }
      try {
        QRebelRestApiClient.createBasic(apiUrl).testConnection(apiToken, appName);
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

    @POST
    public FormValidation doCheckBlank(@QueryParameter String value) {
      return StringUtils.isBlank(value) ? FormValidation.error("Mandatory field") : FormValidation.ok();
    }
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException {
    Fields fields;
    if (run instanceof AbstractBuild) {
      fields = resolveFields((AbstractBuild) run);
    }
    else {
      throw new IllegalStateException("Deprecated Jenkins version. Use version 2.3.x+");
    }

    PrintStream logger = listener.getLogger();
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
      FailureReport report = FailureReport.generate(qRData, fields);
      String initialDescription = run.getDescription();
      run.setDescription(StringUtils.isEmpty(initialDescription) ? report.asHtml() : initialDescription + "<br/>" + report.asHtml());
      logger.println(report.asText());
      run.setResult(Result.FAILURE);
    }
  }

  //  fails a build and add error message to the log if the minimal param set in undefined
  private static void validateMinimalMandatoryParameters(Fields fields) {
    if (StringUtils.isEmpty(fields.appName) || StringUtils.isEmpty(fields.apiUrl) || StringUtils.isEmpty(fields.apiToken)) {
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
    QRebelRestApi restApi = QRebelRestApiClient.create(fields.apiUrl, logger);
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

  private Fields resolveFields(AbstractBuild<?, ?> build) {
    VariableResolver<String> varResolver = build.getBuildVariableResolver();
    return Fields.builder()
        .apiToken(StringUtils.trimToNull(Util.replaceMacro(apiToken, varResolver)))
        .appName(StringUtils.trimToNull(Util.replaceMacro(appName, varResolver)))
        .baselineBuild(StringUtils.trimToNull(Util.replaceMacro(baselineBuild, varResolver)))
        .baselineVersion(StringUtils.trimToNull(Util.replaceMacro(baselineVersion, varResolver)))
        .targetBuild(StringUtils.trimToNull(Util.replaceMacro(targetBuild, varResolver)))
        .targetVersion(StringUtils.trimToNull(Util.replaceMacro(targetVersion, varResolver)))
        .apiUrl(StringUtils.trimToNull(Util.replaceMacro(apiUrl, varResolver)))
        .slowRequestsAllowed(slowRequestsAllowed)
        .exceptionsAllowed(exceptionsAllowed)
        .excessiveIoAllowed(excessiveIoAllowed)
        .slaGlobalLimit(slaGlobalLimit)
        .comparisonStrategy(ComparisonStrategy.valueOf(comparisonStrategy))
        .issueTypes(toIssueTypes())
        .build();
  }

  // Helper method for the jelly view to determine comparisonStrategy
  public String isStrategy(String comparisonStrategy) {
    return StringUtils.equalsIgnoreCase(comparisonStrategy, this.comparisonStrategy) ? "true" : "";
  }
}
