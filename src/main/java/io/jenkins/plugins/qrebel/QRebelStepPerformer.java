/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package io.jenkins.plugins.qrebel;

import java.io.IOException;
import java.io.PrintStream;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.plugins.envinjectapi.util.EnvVarsResolver;

import hudson.model.Build;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.qrebel.rest.BuildClassifier;
import io.jenkins.plugins.qrebel.rest.Issues;
import io.jenkins.plugins.qrebel.rest.QRebelRestApi;
import io.jenkins.plugins.qrebel.rest.QRebelRestApiBuilder;
import lombok.Value;

/**
 * Business logic for QRebelPublisher. A Jenkins build will be marked as failed if there are some issues detected by QRebel
 * The issues are obtained via HTTP requests to the QRebel server. Some of them can be ignored depending on the plugin configuration.
 */
@Value
class QRebelStepPerformer {
  private final Fields fields;
  private final PrintStream logger;
  private final Run<?, ?> run;
  private final QRebelRestApi restApi;

  // build a new class instance
  static QRebelStepPerformer make(QRebelPublisher stepFields, Run<?, ?> run, TaskListener listener) {
    if (run instanceof Build) {
      Fields resolved = resolveFields(stepFields, run);
      PrintStream logger = listener.getLogger();
      QRebelRestApi restApi = QRebelRestApiBuilder.make(resolved.serverUrl);
      return new QRebelStepPerformer(resolved, logger, run, restApi);
    }

    throw new IllegalArgumentException("Deprecated Jenkins version. Use 2.1.0+");
  }

  private static Fields resolveFields(QRebelPublisher fields, Run<?, ?> run) {
    return Fields.builder()
        .apiKey(resolveEnvVarFromRun(fields.apiKey, run))
        .appName(resolveEnvVarFromRun(fields.appName, run))
        .baselineBuild(resolveEnvVarFromRun(fields.baselineBuild, run))
        .baselineVersion(resolveEnvVarFromRun(fields.baselineVersion, run))
        .targetBuild(resolveEnvVarFromRun(fields.targetBuild, run))
        .targetVersion(resolveEnvVarFromRun(fields.targetVersion, run))
        .serverUrl(resolveEnvVarFromRun(fields.serverUrl, run))
        .durationFail(fields.durationFail)
        .exceptionFail(fields.exceptionFail)
        .ioFail(fields.ioFail)
        .threshold(fields.threshold)
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

  // the main flow
  void perform() throws IOException {
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
      run.setDescription(getFailureDescription(qRData, initialDescription, stats.getSlowestDuration()));
      logger.println("Performance regression have been found in the current build. Failing build.");

      logger.println(String.format("Slow Requests: %d%n" +
              " Excessive IO: %d %n" +
              " Exceptions: %d  %n" +
              " SLA global limit (ms): %d ms | slowest endpoint time(ms): %d ms",
          qRData.issuesCount.DURATION, qRData.issuesCount.IO, qRData.issuesCount.EXCEPTIONS, fields.threshold, stats.getSlowestDuration()));

      logger.println("For more details check your <a href=\"" + qRData.appViewUrl + "/\">dashboard</a>");
    }
  }

  // describe failure reason
  private String getFailureDescription(Issues qRData, String buildDescription, long slowestDuration) {
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
