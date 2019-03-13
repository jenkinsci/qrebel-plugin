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
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.plugins.envinjectapi.util.EnvVarsResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.BuildClassifier;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.Issues;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.QRebelRestApi;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.QRebelRestApiClient;

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
  final String apiKey;
  final String serverUrl;
  final long durationFail;
  final long ioFail;
  final long exceptionFail;
  final long threshold;


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
      return Messages.QRebelBuilder_DescriptorImpl_DisplayName();
    }
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException {
    PrintStream logger = listener.getLogger();
    Fields fields = resolveFields(run);
    QRebelRestApi restApi = QRebelRestApiClient.create(fields.serverUrl);

    logger.println("AppName: " + fields.appName);
    logger.println("Baseline Build: " + fields.baselineBuild);
    logger.println("Baseline Version: " + fields.baselineVersion);
    logger.println("Target Build: " + fields.targetBuild);
    logger.println("Target Version: " + fields.targetVersion);

    Issues qRData;
    if (StringUtils.isNotEmpty(fields.baselineBuild)) {
      restApi.setDefaultBaseline(fields.apiKey, fields.appName, new BuildClassifier(fields.baselineBuild, fields.baselineVersion));
      qRData = restApi.getIssuesVsBaseline(fields.apiKey, fields.appName, fields.targetBuild, fields.targetVersion, fields.durationFail, fields.ioFail, fields.exceptionFail, PluginVersion.get());
    }
    else {
      qRData = restApi.getIssuesVsThreshold(fields.apiKey, fields.appName, fields.targetBuild, fields.targetVersion, fields.durationFail, fields.ioFail, fields.exceptionFail, PluginVersion.get());
    }
    IssuesStats stats = new IssuesStats(qRData);

    boolean failBuild = qRData.issuesCount.DURATION > fields.durationFail
        || qRData.issuesCount.IO > fields.ioFail
        || qRData.issuesCount.EXCEPTIONS > fields.exceptionFail
        || stats.isThresholdProvidedAndExceeded(fields.threshold);

    if (failBuild) {
      run.setResult(Result.FAILURE);
      String initialDescription = run.getDescription();
      run.setDescription(getFailureDescription(qRData, fields, initialDescription, stats.getSlowestDuration()));
      logger.println("Performance regression have been found in the current build. Failing build.");

      logger.println(String.format("Slow Requests: %d%n" +
              " Excessive IO: %d %n" +
              " Exceptions: %d  %n" +
              " SLA global limit (ms): %d ms | slowest endpoint time(ms): %d ms",
          qRData.issuesCount.DURATION, qRData.issuesCount.IO, qRData.issuesCount.EXCEPTIONS, fields.threshold, stats.getSlowestDuration()));

      logger.println("For more details check your <a href=\"" + qRData.appViewUrl + "/\">dashboard</a>");
    }
  }

  private Fields resolveFields(Run<?, ?> run) {
    return Fields.builder()
        .apiKey(resolveEnvVarFromRun(apiKey, run))
        .appName(resolveEnvVarFromRun(appName, run))
        .baselineBuild(resolveEnvVarFromRun(baselineBuild, run))
        .baselineVersion(resolveEnvVarFromRun(baselineVersion, run))
        .targetBuild(resolveEnvVarFromRun(targetBuild, run))
        .targetVersion(resolveEnvVarFromRun(targetVersion, run))
        .serverUrl(resolveEnvVarFromRun(serverUrl, run))
        .durationFail(durationFail)
        .exceptionFail(exceptionFail)
        .ioFail(ioFail)
        .threshold(threshold)
        .build();
  }

  // resolve fields, NotNull
  private static String resolveEnvVarFromRun(String value, Run<?, ?> run) {
    try {
      String resolved = EnvVarsResolver.resolveEnvVars(run, value);
      return resolved == null ? "" : resolved;
    }
    catch (EnvInjectException e) {
      throw new IllegalStateException("Unable to get Env Variable " + value, e);
    }
  }

  // describe failure reason
  private static String getFailureDescription(Issues qRData, Fields fields, String buildDescription, long slowestDuration) {
    StringBuilder descriptionBuilder = new StringBuilder();
    if (StringUtils.isNotEmpty(buildDescription)) {
      descriptionBuilder.append(buildDescription);
    }
    descriptionBuilder.append(String.format("Failing build due to performance regressions found in %s compared to build %s version %s. <br/>" +
            "Slow Requests: %d <br/>" +
            "Excessive IO: %d <br/>" +
            "Exceptions: %d <br/>" +
            "SLA global limit (ms): %d ms | slowest endpoint time(ms): %d ms <br/>" +
            "For full report check your <a href= %s >dashboard</a>.<br/>",
        qRData.appName, fields.baselineBuild, fields.baselineVersion, qRData.issuesCount.DURATION,
        qRData.issuesCount.IO, qRData.issuesCount.EXCEPTIONS,
        fields.threshold, slowestDuration, qRData.appViewUrl));

    return descriptionBuilder.toString();
  }
}
