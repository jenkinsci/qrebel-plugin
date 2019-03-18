/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.zeroturnaround.jenkins.plugin.qrebel.ComparisonStrategy.BASELINE;
import static org.zeroturnaround.jenkins.plugin.qrebel.ComparisonStrategy.DEFAULT_BASELINE;
import static org.zeroturnaround.jenkins.plugin.qrebel.ComparisonStrategy.THRESHOLD;

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

  private static final String APP_NAME = "foobar";
  private static final String TARGET_BUILD = "2.0.6RC3";
  private static final String TARGET_VERSION = "1";
  private static final String BASELINE_BUILD = "2.05RC1";
  private static final String BASELINE_VERSION = TARGET_VERSION;

  private static final String AUTH_KEY = "correct-key";

  private static final long IGNORE_ALL_SLOW_REQUESTS = 15L;
  private static final long TOO_MANY_SLOW_REQUESTS = IGNORE_ALL_SLOW_REQUESTS - 1L;
  private static final long IGNORE_ALL_IO_ISSUES = 0L;
  private static final long IGNORE_ALL_EXCEPTIONS = 2L;
  private static final long TOO_MANY_EXCEPTIONS = IGNORE_ALL_EXCEPTIONS - 1L;
  private static final long FASTEST_REQUEST = 26L;
  private static final long SLOWEST_REQUEST = 3770L;
  private static final long GLOBAL_LIMIT_BELOW_FASTEST = FASTEST_REQUEST - 1L;
  private static final long GLOBAL_LIMIT_ABOVE_SLOWEST = SLOWEST_REQUEST + 1L;

  @Test
  public void authFailedOnIssues() throws Exception {
    stubIssuesApi(forbidden());
    buildAndAssertFailure(makeDefault());
    verifyIssuesCalled();
  }

  @Test
  public void tooManySlowRequests() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertFailure(makeWithAllowedIssues(TOO_MANY_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS));
  }

  @Test
  public void tooManyExceptions() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertFailure(makeWithAllowedIssues(IGNORE_ALL_SLOW_REQUESTS, TOO_MANY_EXCEPTIONS));
  }

  @Test
  public void limitBelowFastest() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertFailure(makeWithThreshold(GLOBAL_LIMIT_BELOW_FASTEST));
  }

  @Test
  public void limitTouchesFastest() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertFailure(makeWithThreshold(FASTEST_REQUEST));
  }

  @Test
  public void limitTouchesSlowest() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertFailure(makeWithThreshold(SLOWEST_REQUEST));
  }

  @Test
  public void limitAboveSlowest() throws Exception {
    stubIssuesApi(ok());
    j.buildAndAssertSuccess(makeWithThreshold(GLOBAL_LIMIT_ABOVE_SLOWEST));
    verifyIssuesCalled();
  }

  @Test
  public void baselineStrategySet() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertSuccess(makeWithStrategy(BASELINE));
    verifyStrategySet(BASELINE);
  }

  @Test
  public void globalThresholdStrategySet() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertSuccess(makeWithStrategy(THRESHOLD));
    verifyStrategySet(THRESHOLD);
  }

  @Test
  public void defaultBaselineStrategySet() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertSuccess(makeWithStrategy(DEFAULT_BASELINE));
    verifyStrategySet(DEFAULT_BASELINE);
  }

  @Test
  public void userLimitsSet() throws Exception {
    stubIssuesApi(ok());
    buildAndAssertSuccess(makeDefault());
    verifyLimitsAndProtocolSet(IGNORE_ALL_SLOW_REQUESTS);
  }

  private void stubIssuesApi(ResponseDefinitionBuilder response) throws IOException {
    String issuesJson = IOUtils.toString(this.getClass().getResourceAsStream("issues.json"));
    stubFor(get(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withHeader("authorization", equalTo(AUTH_KEY))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .withQueryParam("targetVersion", equalTo(TARGET_VERSION))
        .willReturn(response.withBody(issuesJson)));
  }

  private void verifyIssuesCalled() {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .withQueryParam("targetVersion", equalTo(TARGET_VERSION))
        .withHeader("authorization", equalTo(AUTH_KEY));
    verify(patternBuilder);
  }

  private void verifyStrategySet(ComparisonStrategy strategy) {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"));

    if (strategy == DEFAULT_BASELINE) {
      patternBuilder = patternBuilder
          .withQueryParam("defaultBaseline", containing(""))
          .withQueryParam("baselineBuild", absent())
          .withQueryParam("baselineVersion", absent());
    }
    else if (strategy == ComparisonStrategy.BASELINE) {
      patternBuilder = patternBuilder
          .withQueryParam("defaultBaseline", absent())
          .withQueryParam("baselineBuild", equalTo(BASELINE_BUILD))
          .withQueryParam("baselineVersion", equalTo(BASELINE_VERSION));
    }
    else {
      patternBuilder = patternBuilder
          .withQueryParam("defaultBaseline", absent())
          .withQueryParam("baselineBuild", absent())
          .withQueryParam("baselineVersion", absent());
    }

    verify(patternBuilder);

  }

  private void verifyLimitsAndProtocolSet(long slowRequestsAllowed) {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("slowRequestsAllowed", equalTo(String.valueOf(slowRequestsAllowed)))
        .withQueryParam("excessiveIOAllowed", equalTo(String.valueOf(IGNORE_ALL_IO_ISSUES)))
        .withQueryParam("exceptionsAllowed", equalTo(String.valueOf(IGNORE_ALL_EXCEPTIONS)))
        .withQueryParam("jenkinsPluginVersion", containing("1"));
    verify(patternBuilder);
  }

  private void setEnvVariables(String baselineBuild, long durationFail, long exceptionFail, long threshold, ComparisonStrategy strategy) {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("appName", APP_NAME);
    env.put("targetBuild", TARGET_BUILD);
    env.put("targetVersion", TARGET_VERSION);
    env.put("baselineBuild", baselineBuild);
    env.put("baselineVersion", BASELINE_VERSION);
    env.put("apiKey", AUTH_KEY);
    env.put("serverUrl", wireMockRule.baseUrl());
    env.put("slowRequestsAllowed", String.valueOf(durationFail));
    env.put("excessiveIOAllowed", String.valueOf(IGNORE_ALL_IO_ISSUES));
    env.put("exceptionsAllowed", String.valueOf(exceptionFail));
    env.put("slaGlobalLimit", String.valueOf(threshold));
    env.put("comparisonStrategy", strategy.getName());
    j.jenkins.getGlobalNodeProperties().add(prop);
  }

  private FreeStyleProject makeDefault() throws IOException {
    return makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE);
  }

  private FreeStyleProject makeWithAllowedIssues(long durationFail, long exceptionFail) throws IOException {
    return makeProject(BASELINE_BUILD, durationFail, exceptionFail, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE);
  }

  private FreeStyleProject makeWithThreshold(long threshold) throws IOException {
    return makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, threshold, DEFAULT_BASELINE);
  }

  private FreeStyleProject makeWithStrategy(ComparisonStrategy strategy) throws IOException {
    return makeProject(BASELINE_BUILD, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, strategy);
  }

  private FreeStyleProject makeProject(String baselineBuild, long durationFail, long exceptionFail, long threshold, ComparisonStrategy strategy) throws IOException {
    setEnvVariables(baselineBuild, durationFail, exceptionFail, threshold, strategy);
    FreeStyleProject project = j.createFreeStyleProject();
    project.getPublishersList().add(new QRebelPublisher(APP_NAME, TARGET_BUILD, TARGET_VERSION, baselineBuild, BASELINE_VERSION, AUTH_KEY, wireMockRule.baseUrl(), strategy.getName(), durationFail, IGNORE_ALL_IO_ISSUES, exceptionFail, threshold));
    return project;
  }

  private void buildAndAssertFailure(FreeStyleProject project) throws Exception {
    j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
  }

  private void buildAndAssertSuccess(FreeStyleProject project) throws Exception {
    j.buildAndAssertSuccess(project);
  }
}
