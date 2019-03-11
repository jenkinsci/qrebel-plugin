package io.jenkins.plugins.qrebel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.plugins.envinjectapi.util.EnvVarsResolver;

import feign.Feign;
import feign.Response;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import hudson.model.Build;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import lombok.Value;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Business logic for QRebelPublisher. A Jenkins build will be marked as failed if there are some issues detected by QRebel
 * The issues are obtained via HTTP requests to the QRebel server. Some of them can be ignored depending on the plugin configuration.
 */

@Value
class QRebelStepPerformer {
  private final Fields fields;
  private final PrintStream logger;
  private final Run<?, ?> run;
  private final QRebelRestApi restApi;

  // extract response body if HTTP request fails
  private static class ErrorBodyDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
      try {
        return new IllegalStateException(IOUtils.toString(response.body().asInputStream()));
      }
      catch (IOException e) {
        return new IllegalStateException(response.toString(), e);
      }
    }
  }

  // build a new class instance
  static QRebelStepPerformer make(QRebelPublisher stepFields, Run<?, ?> run, TaskListener listener) {
    if (run instanceof Build) {
      Fields resolved = resolveFields(stepFields, run);
      PrintStream logger = listener.getLogger();
      QRebelRestApi restApi = Feign.builder()
          .errorDecoder(new ErrorBodyDecoder())
          .encoder(new GsonEncoder())
          .decoder(new GsonDecoder())
          .target(QRebelRestApi.class, stepFields.serverUrl);
      return new QRebelStepPerformer(resolved, logger, run, restApi);
    }

    throw new IllegalArgumentException("Deprecated Jenkins version. Use 2.1.0+");
  }

  // the main flow
  void perform() throws IOException {
    logger.println("AppName: " + fields.appName);
    logger.println("Baseline Build: " + fields.baselineBuild);
    logger.println("Baseline Version: " + fields.baselineVersion);
    logger.println("Target Build: " + fields.targetBuild);
    logger.println("Target Version: " + fields.targetVersion);

    Issues qRData;
    if (StringUtils.isNotEmpty(fields.targetBuild)) {
      restApi.setDefaultBaseline(fields.apiKey, fields.appName, new BuildClassifier(fields.baselineBuild, fields.baselineVersion));
      qRData = restApi.getIssuesVsBaseline(fields.apiKey, fields.appName, fields.targetBuild, fields.targetVersion, fields.durationFail, fields.ioFail, fields.exceptionFail, PluginVersion.get());
    }
    else {
      qRData = restApi.getIssuesVsThreshold(fields.apiKey, fields.appName, fields.targetBuild, fields.targetVersion, fields.durationFail, fields.ioFail, fields.exceptionFail, PluginVersion.get());
    }

    boolean failBuild = qRData.issuesCount.DURATION > fields.durationFail
        || qRData.issuesCount.IO > fields.ioFail
        || qRData.issuesCount.EXCEPTIONS > fields.exceptionFail
        || isThresholdProvidedAndExceeded(qRData);

    if (failBuild) {
      run.setResult(Result.FAILURE);
      String initialDescription = run.getDescription();
      run.setDescription(getFailureDescription(qRData, initialDescription));
      logger.println("Performance regression have been found in the current build. Failing build.");

      logger.println(String.format("Slow Requests: %d%n" +
              " Excessive IO: %d %n" +
              " Exceptions: %d  %n" +
              " SLA global limit (ms): %d ms | slowest endpoint time(ms): %d ms",
          qRData.issuesCount.DURATION, qRData.issuesCount.IO, qRData.issuesCount.EXCEPTIONS, fields.threshold, getSlowestDelay(qRData)));

      logger.println("For more detail check your <a href=\"" + qRData.appViewUrl + "/\">dashboard</a>");
    }
  }

  // check if found issues are too slow
  private boolean isThresholdProvidedAndExceeded(Issues qRData) {
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

  private int getSlowestDelay(Issues qRData) {
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

  // describe failure reason
  private String getFailureDescription(Issues qRData, String buildDescription) {
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
        fields.threshold, getSlowestDelay(qRData), qRData.appViewUrl));

    return descriptionBuilder.toString();
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

  private static String resolveEnvVarFromRun(String value, Run<?, ?> run) {
    try {
      String resolved = EnvVarsResolver.resolveEnvVars(run, value);
      return resolved == null? "" : resolved;
    }
    catch (EnvInjectException e) {
      throw new IllegalStateException("Unable to get Env Variable " + value, e);
    }
  }
}
