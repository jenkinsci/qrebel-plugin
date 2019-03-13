/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package io.jenkins.plugins.qrebel;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;

public class QRebelTestPublisherTest {

  @Rule
  public JenkinsRule j = new JenkinsRule();
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

  private static final String APP_NAME = "foo";
  private static final String TARGET_BUILD = "2.0.6RC3";
  private static final String TARGET_VERSION = "1";
  private static final String BASELINE_BUILD = "2.05RC1";
  private static final String NO_BASELINE_BUILD = "";
  private static final String BASELINE_VERSION = TARGET_VERSION;

  private static final String AUTH_KEY = "correct-key";

  private static final int IGNORE_ALL_SLOW_REQUESTS = 15;
  private static final int TOO_MANY_SLOW_REQUESTS = IGNORE_ALL_SLOW_REQUESTS - 1;
  private static final int IGNORE_ALL_IO_ISSUES = 0;
  private static final int IGNORE_ALL_EXCEPTIONS = 2;
  private static final int TOO_MANY_EXCEPTIONS = IGNORE_ALL_EXCEPTIONS - 1;
  private static final int FASTEST_REQUEST = 26;
  private static final int SLOWEST_REQUEST = 3770;
  private static final int THRESHOLD_BELOW_FASTEST = FASTEST_REQUEST - 1;
  private static final int THRESHOLD_ABOVE_SLOWEST = SLOWEST_REQUEST + 1;


  @Test
  public void authFailedOnBaseline() throws Exception {
    stubAuthApi(forbidden());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyBaselineCalled();
  }

  @Test
  public void authFailedOnIssues() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(forbidden());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyIssuesCalled(true);
  }

  @Test
  public void tooManySlowRequests() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, TOO_MANY_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
  }

  @Test
  public void tooManyExceptions() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, TOO_MANY_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
  }

  @Test
  public void thresholdBelowFastest() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_BELOW_FASTEST));
  }

  @Test
  public void thresholdTouchesFastest() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, FASTEST_REQUEST));
  }

  @Test
  public void thresholdTouchesSlowest() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    buildAndAssertFailure(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, SLOWEST_REQUEST));
  }

  @Test
  public void thresholdAboveSlowest() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    j.buildAndAssertSuccess(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyBaselineCalled();
    verifyIssuesCalled(true);
  }

  @Test
  public void noDefaultBaseline() throws Exception {
    stubIssuesApi(ok());
    j.buildAndAssertSuccess(makeProject(NO_BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyIssuesCalled(false);
  }

  @Test
  public void userLimitsSet() throws Exception {
    stubAuthApi(ok());
    stubIssuesApi(ok());
    j.buildAndAssertSuccess(makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyLimitsAndProtocolSet(IGNORE_ALL_SLOW_REQUESTS);
  }

  private void stubAuthApi(ResponseDefinitionBuilder response) {
    stubFor(put("/api/applications/" + APP_NAME + "/baselines/default/")
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo(AUTH_KEY))
        .willReturn(response));
  }

  private void verifyBaselineCalled() {
    verify(putRequestedFor(urlEqualTo("/api/applications/" + APP_NAME + "/baselines/default/"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo(AUTH_KEY))
    );
  }

  private void stubIssuesApi(ResponseDefinitionBuilder response) throws IOException {
    String issuesJson = IOUtils.toString(this.getClass().getResourceAsStream("issues.json"));
    stubFor(get(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withHeader("authorization", equalTo(AUTH_KEY))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .withQueryParam("targetVersion", equalTo(TARGET_VERSION))
        .willReturn(response.withBody(issuesJson)));
  }

  private void verifyIssuesCalled(boolean withDefaultBaseline) {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .withQueryParam("targetVersion", equalTo(TARGET_VERSION))
        .withQueryParam("defaultBaseline", containing(""))
        .withHeader("authorization", equalTo(AUTH_KEY))
        .withQueryParam("defaultBaseline", withDefaultBaseline? containing("") : absent());
    verify(patternBuilder);
  }

  private void verifyLimitsAndProtocolSet(int slowRequestsAllowed) {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("slowRequestsAllowed", equalTo(String.valueOf(slowRequestsAllowed)))
        .withQueryParam("excessiveIOAllowed", equalTo(String.valueOf(IGNORE_ALL_IO_ISSUES)))
        .withQueryParam("exceptionsAllowed", equalTo(String.valueOf(IGNORE_ALL_EXCEPTIONS)))
        .withQueryParam("jenkinsPluginVersion", containing("1"));
    verify(patternBuilder);
  }

  private void setEnvVariables(String baselineBuild, int durationFail, int exceptionFail, int threshold) {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("appName", APP_NAME);
    env.put("targetBuild", TARGET_BUILD);
    env.put("targetVersion", TARGET_VERSION);
    env.put("baselineBuild", baselineBuild);
    env.put("baselineVersion", BASELINE_VERSION);
    env.put("apiKey", AUTH_KEY);
    env.put("serverUrl", wireMockRule.baseUrl());
    env.put("durationFail", String.valueOf(durationFail));
    env.put("ioFail", String.valueOf(IGNORE_ALL_IO_ISSUES));
    env.put("exceptionFail", String.valueOf(exceptionFail));
    env.put("threshold", String.valueOf(threshold));
    j.jenkins.getGlobalNodeProperties().add(prop);
  }

  private FreeStyleProject makeProject(String baselineBuild, int durationFail, int exceptionFail, int threshold) throws IOException {
    setEnvVariables(baselineBuild, durationFail, exceptionFail, threshold);
    FreeStyleProject project = j.createFreeStyleProject();
    project.getPublishersList().add(new QRebelPublisher(APP_NAME, TARGET_BUILD, TARGET_VERSION, baselineBuild, BASELINE_VERSION, AUTH_KEY, wireMockRule.baseUrl(), durationFail, IGNORE_ALL_IO_ISSUES, exceptionFail, threshold));
    return project;
  }

  private void buildAndAssertFailure(FreeStyleProject project) throws Exception {
    j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
  }
}
