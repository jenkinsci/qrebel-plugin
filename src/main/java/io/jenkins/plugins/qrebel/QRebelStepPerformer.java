package io.jenkins.plugins.qrebel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import hudson.model.Build;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

class QRebelStepPerformer {
  private final QRebelPublisher fields;
  private final ParameterResolver parameterResolver;
  private final PrintStream logger;
  private final Run<?, ?> run;

  private QRebelStepPerformer(QRebelPublisher fields, ParameterResolver parameterResolver, PrintStream logger, Run<?, ?> run) {
    this.fields = fields;
    this.parameterResolver = parameterResolver;
    this.logger = logger;
    this.run = run;
  }

  static QRebelStepPerformer make(QRebelPublisher stepFields, Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
    if (run instanceof Build) {
      return new QRebelStepPerformer(stepFields, ParameterResolver.make((Build) run, listener), listener.getLogger(), run);
    }

    throw new IllegalArgumentException("Deprecated Jenkins version. Use 2.1.0+");
  }

  void perform() throws IOException {
    logger.println("AppName" + fields.getAppName() + " resolveAppName " + parameterResolver.get(fields.getAppName()));
    logger.println("Baseline Build: " + fields.getBaselineBuild());
    logger.println("Target Build: " + fields.getTargetBuild() + " resolved: " + parameterResolver.get(fields.getTargetBuild()));

    setRemoteBaseline();

    logger.println("Going to perform QRebel API call..");
    String apiUrl = buildApiUrl();
    logger.println("Calling URL - " + apiUrl);
    String issues = getIssuesJson(apiUrl);
    QRebelData qRData = QRebelData.parse(issues);

    boolean failBuild = qRData.getDurationCount() > fields.durationFail
        || qRData.getIOCount() > fields.ioFail
        || qRData.getExceptionCount() > fields.exceptionFail
        || isThresholdProvidedAndExceeded(qRData);

    if (failBuild) {
      run.setResult(Result.FAILURE);
      String initialDescription = run.getDescription();
      run.setDescription(getFailureDescription(qRData, initialDescription));
      logger.println("Performance regression have been found in the current build. Failing build.");

      logger.println(String.format("Slow Requests: %d%n" +
              " Excessive IO: %d %n" +
              " Exceptions: %d  %n" +
              " Threshold limit(ms): %d ms | slowest endpoint time(ms): %d ms",
          qRData.getDurationCount(), qRData.getIOCount(), qRData.getExceptionCount(), fields.threshold, maximumDelay(qRData)));

      logger.println("For more detail check your <a href=\"" + qRData.getViewUrl() + "/\">dashboard</a>");
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

  private String getIssuesJson(String apiUrl) throws IOException {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(apiUrl);
      httpGet.setHeader("authorization", fields.getApiKey());
      try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
        return EntityUtils.toString(response.getEntity());
      }
    }
  }

  private void setRemoteBaseline() throws IOException {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpPut httpPut = new HttpPut(fields.getServelUrl() + parameterResolver.get(fields.getAppName()) + "/baselines/default/");
      httpPut.setHeader("Content-Type", "application/json");
      httpPut.setHeader("authorization", fields.getApiKey());
      if (StringUtils.isNotEmpty(fields.getBaselineBuild())) {
        httpPut.setEntity(new StringEntity("{ \"build\": \"" + fields.getBaselineBuild() + "\" }"));
      }
      httpclient
          .execute(httpPut)
          .close();
    }
  }

  private String buildApiUrl() {
    return fields.getServelUrl() +
        parameterResolver.get(fields.getAppName()) +
        "/" +
        "issues" +
        "/?targetBuild=" +
        parameterResolver.get(fields.getTargetBuild()) +
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
        qRData.getAppName(), parameterResolver.get(fields.getBaselineBuild()), qRData.getDurationCount(), qRData.getIOCount(),
        qRData.getExceptionCount(), fields.threshold, maximumDelay(qRData), qRData.getViewUrl()));

    return descriptionBuilder.toString();
  }
}
