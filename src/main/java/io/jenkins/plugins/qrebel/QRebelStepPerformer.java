package io.jenkins.plugins.qrebel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.plugins.envinjectapi.util.EnvVarsResolver;

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
 *
 * Business logic for QRebelPublisher. A Jenkins build will be marked as failed if there are some issues detected by QRebel
 * The issues are obtained via HTTP requests to the QRebel server. Some of them can be ignored depending on the plugin configuration.
 */

class QRebelStepPerformer {
  private final QRebelPublisher fields;
  private final PrintStream logger;
  private final Run<?, ?> run;

  private QRebelStepPerformer(QRebelPublisher fields, PrintStream logger, Run<?, ?> run) {
    this.fields = fields;
    this.logger = logger;
    this.run = run;
  }

  // build a new class instance
  static QRebelStepPerformer make(QRebelPublisher stepFields, Run<?, ?> run, TaskListener listener) {
    if (run instanceof Build) {
      return new QRebelStepPerformer(stepFields, listener.getLogger(), run);
    }

    throw new IllegalArgumentException("Deprecated Jenkins version. Use 2.1.0+");
  }

  // the main flow
  void perform() throws IOException {
    logger.println("AppName" + fields.getAppName() + " resolveAppName " + resolveEnvVars(fields.getAppName()));
    logger.println("Baseline Build: " + fields.getBaselineBuild() + " resolved: " + resolveEnvVars(fields.getBaselineBuild()));
    logger.println("Target Build: " + fields.getTargetBuild() + " resolved: " + resolveEnvVars(fields.getTargetBuild()));

    String baselineApiUrl = getBaselineApiUrl();
    logger.println("Setting baseline, URL " + baselineApiUrl);
    setBaseline(baselineApiUrl);

    String issuesApiUrl = getIssuesApiUrl();
    logger.println("Retrieving issues list, URL " + issuesApiUrl);
    QRebelData qRData = QRebelData.parse(getIssuesAsJson(issuesApiUrl));

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
          qRData.getDurationCount(), qRData.getIOCount(), qRData.getExceptionCount(), fields.threshold, getSlowestDelay(qRData)));

      logger.println("For more detail check your <a href=\"" + qRData.getViewUrl() + "/\">dashboard</a>");
    }
  }

  // check if found issues are too slow
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

  private int getSlowestDelay(QRebelData qRData) {
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

  // call remote API to set Baseline
  private void setBaseline(String baselineApiUrl) throws IOException {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpPut httpPut = new HttpPut(baselineApiUrl);
      httpPut.setHeader("Content-Type", "application/json");
      httpPut.setHeader("authorization", fields.getApiKey());
      String baselineBuild = resolveEnvVars(fields.getBaselineBuild());
      if (StringUtils.isNotEmpty(baselineBuild)) {
        httpPut.setEntity(new StringEntity("{ \"build\": \"" + baselineBuild + "\" }", ContentType.APPLICATION_JSON));
      }
      try (CloseableHttpResponse response = httpclient.execute(httpPut)) {
        checkHttpFailure(response);
      }
    }
  }

  // call remote API to get issues
  private String getIssuesAsJson(String apiUrl) throws IOException {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(apiUrl);
      httpGet.setHeader("authorization", fields.getApiKey());
      try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
        checkHttpFailure(response);
        return EntityUtils.toString(response.getEntity());
      }
    }
  }

  private String getBaselineApiUrl() {
    return fields.getServelUrl()
        + "/api/applications/"
        + resolveEnvVars(fields.getAppName())
        + "/baselines/default/";
  }

  private String getIssuesApiUrl() {
    return fields.getServelUrl() + "/api/applications/"
        + resolveEnvVars(fields.getAppName())
        + "/issues/?targetBuild="
        + resolveEnvVars(fields.getTargetBuild())
        + "&defaultBaseline";
  }

  // throw IllegalStateException if HTTP Request status is not OK
  private void checkHttpFailure(HttpResponse response) {
    int code = response.getStatusLine().getStatusCode();
    if (code < HttpStatus.SC_OK || code > HttpStatus.SC_MULTI_STATUS) {
      throw new IllegalStateException("Http request failed with status " + code);
    }
  }

  // describe failure reason
  private String getFailureDescription(QRebelData qRData, String buildDescription) {
    StringBuilder descriptionBuilder = new StringBuilder();
    if (StringUtils.isNotEmpty(buildDescription)) {
      descriptionBuilder.append(buildDescription);
    }
    descriptionBuilder.append(String.format("Failing build due to performance regressions found in %s compared to %s. <br/>" +
            "Slow Requests: %d <br/>" +
            "Excessive IO: %d <br/>" +
            "Exceptions: %d <br/>" +
            "Threshold limit(ms): %d ms | slowest endpoint time(ms): %d ms <br/>" +
            "For full report check your <a href= %s >dashboard</a>.<br/>",
        qRData.getAppName(), resolveEnvVars(fields.getBaselineBuild()), qRData.getDurationCount(), qRData.getIOCount(),
        qRData.getExceptionCount(), fields.threshold, getSlowestDelay(qRData), qRData.getViewUrl()));

    return descriptionBuilder.toString();
  }

  private String resolveEnvVars(String value) {
    try {
      return EnvVarsResolver.resolveEnvVars(run, value);
    }
    catch (EnvInjectException e) {
      throw new IllegalStateException("Unable to get Env Variable " + value, e);
    }
  }
}
