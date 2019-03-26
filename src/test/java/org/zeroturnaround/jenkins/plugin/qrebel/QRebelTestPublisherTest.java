/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.zeroturnaround.jenkins.plugin.qrebel.ComparisonStrategy.BASELINE;
import static org.zeroturnaround.jenkins.plugin.qrebel.ComparisonStrategy.DEFAULT_BASELINE;
import static org.zeroturnaround.jenkins.plugin.qrebel.ComparisonStrategy.THRESHOLD;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import hudson.EnvVars;
import hudson.model.Build;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Publisher;

public class QRebelTestPublisherTest {

  private static final String APP_NAME = "foobar";
  private static final String TARGET_BUILD = "2.0.6RC3";
  private static final String TARGET_VERSION = "1";
  private static final String EMPTY_VERSION = "";
  private static final String BASELINE_BUILD = "2.05RC1";
  private static final String BASELINE_VERSION = TARGET_VERSION;
  private static final String API_TOKEN = "correct-key";
  private static final String EMPTY_TOKEN = "";
  private static final String EMPTY_TARGET_BUILD = "";
  private static final String EMPTY_BASELINE_BUILD = "";
  private static final long IGNORE_ALL_SLOW_REQUESTS = 15L;
  private static final long TOO_MANY_SLOW_REQUESTS = IGNORE_ALL_SLOW_REQUESTS - 1L;
  private static final long IGNORE_ALL_EXCESSIVE_IO_ISSUES = 0L;
  private static final long IGNORE_ALL_EXCEPTIONS = 2L;
  private static final long TOO_MANY_EXCEPTIONS = IGNORE_ALL_EXCEPTIONS - 1L;
  private static final long FASTEST_REQUEST = 26L;
  private static final long SLOWEST_REQUEST = 3770L;
  private static final long GLOBAL_LIMIT_BELOW_FASTEST = FASTEST_REQUEST - 1L;
  private static final long GLOBAL_LIMIT_ABOVE_SLOWEST = SLOWEST_REQUEST + 1L;
  @Rule
  public final JenkinsRule j = new JenkinsRule();
  @Rule
  public final WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

  @Test
  public void authFailedOnIssues() throws Exception {
    stubIssuesApi(forbidden());
    buildAndAssertFailure(makeDefault());
    verifyIssuesCalled();
  }

  @Test
  public void tooManySlowRequests() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(makeWithAllowedIssues(TOO_MANY_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS));
  }

  @Test
  public void tooManyExceptions() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(makeWithAllowedIssues(IGNORE_ALL_SLOW_REQUESTS, TOO_MANY_EXCEPTIONS));
  }

  @Test
  public void limitBelowFastest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(makeWithThreshold(GLOBAL_LIMIT_BELOW_FASTEST));
  }

  @Test
  public void limitTouchesFastest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(makeWithThreshold(FASTEST_REQUEST));
  }

  @Test
  public void limitTouchesSlowest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(makeWithThreshold(SLOWEST_REQUEST));
  }

  @Test
  public void limitAboveSlowest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    j.buildAndAssertSuccess(makeWithThreshold(GLOBAL_LIMIT_ABOVE_SLOWEST));
    verifyIssuesCalled();
  }

  @Test
  public void baselineStrategySet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithStrategy(BASELINE));
    verifyStrategySet(BASELINE);
  }

  @Test
  public void globalThresholdStrategySet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithStrategy(THRESHOLD));
    verifyStrategySet(THRESHOLD);
  }

  @Test
  public void defaultBaselineStrategySet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithStrategy(DEFAULT_BASELINE));
    verifyStrategySet(DEFAULT_BASELINE);
  }

  @Test
  public void userLimitsSet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeDefault());
    verifyLimitsAndProtocolSet();
  }

  @Test
  public void noIssueTypes() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithIssueTypes());
    verifyIssueTypesSet();
  }

  @Test
  public void allIssueTypes() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithIssueTypes(IssueType.EXCEPTIONS, IssueType.DURATION, IssueType.IO));
    verifyIssueTypesSet(IssueType.EXCEPTIONS, IssueType.DURATION, IssueType.IO);
  }

  @Test
  public void onlyDurationIssueType() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithIssueTypes(IssueType.DURATION));
    verifyIssueTypesSet(IssueType.DURATION);
  }

  @Test
  public void onlyIoIssueType() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithIssueTypes(IssueType.IO));
    verifyIssueTypesSet(IssueType.IO);
  }

  @Test
  public void onlyExceptionIssueType() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithIssueTypes(IssueType.EXCEPTIONS));
    verifyIssueTypesSet(IssueType.EXCEPTIONS);
  }

  @Test
  public void emptyVersions() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(makeWithEmptyVersions());
    verifyEmptyVersions();
  }

  @Test
  public void jsonErrorShownInLogs() throws Exception {
    stubIssuesApi(400, "{ error: 'You shall not pass!'}");
    Build build = buildAndAssertFailure(makeWithEmptyVersions());
    j.assertLogContains("You shall not pass!", build);
  }

  @Test
  public void nonJsonErrorShownInLogs() throws Exception {
    stubIssuesApi(400, "A message");
    Build build = buildAndAssertFailure(makeWithEmptyVersions());
    j.assertLogContains("A message", build);
  }

  @Test
  public void connectionParamsCannotBeBlank() throws Exception {
    Build build = buildAndAssertFailure(makeWithEmptyApiToken());
    j.assertLogContains("Connection parameters cannot be blank", build);
  }

  @Test
  public void targetBuildCannotBeBlank() throws Exception {
    Build build = buildAndAssertFailure(makeWithEmptyTargetBuild());
    j.assertLogContains("Target build name cannot be blank", build);
  }

  @Test
  public void baselineBuildCannotBeBlankInBaseline() throws Exception {
    Build build = buildAndAssertFailure(makeWithEmptyMandatoryBaselineBuild());
    j.assertLogContains("Baseline build name cannot be blank", build);
  }

  @Test
  public void baselineBuildCanBeBlank() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertSuccess(makeWithEmptyOptionalBaselineBuild());
    j.assertLogNotContains("Baseline build name cannot be blank", build);
  }

  private void stubIssuesApi(ResponseDefinitionBuilder response) throws IOException {
    stubFor(get(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withHeader("authorization", equalTo(API_TOKEN))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .willReturn(response));
  }

  private void stubIssuesApi(int status, String responseBody) throws IOException {
    stubIssuesApi(aResponse().withStatus(status).withBody(responseBody));
  }

  private String getIssuesJson() {
    try {
      return IOUtils.toString(this.getClass().getResourceAsStream("issues.json"));
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void verifyIssuesCalled() {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .withQueryParam("targetVersion", equalTo(TARGET_VERSION))
        .withHeader("authorization", equalTo(API_TOKEN));
    verify(patternBuilder);
  }

  private void verifyStrategySet(ComparisonStrategy strategy) {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"));

    if (strategy == DEFAULT_BASELINE) {
      patternBuilder = patternBuilder
          .withQueryParam("defaultBaseline", equalTo("true"))
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

  private void verifyEmptyVersions() {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("baselineVersion", absent())
        .withQueryParam("targetVersion", absent());

    verify(patternBuilder);
  }

  private void verifyLimitsAndProtocolSet() {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withQueryParam("slowRequestsAllowed", equalTo(String.valueOf(IGNORE_ALL_SLOW_REQUESTS)))
        .withQueryParam("excessiveIOAllowed", equalTo(String.valueOf(IGNORE_ALL_EXCESSIVE_IO_ISSUES)))
        .withQueryParam("exceptionsAllowed", equalTo(String.valueOf(IGNORE_ALL_EXCEPTIONS)))
        .withQueryParam("jenkinsPluginVersion", matching(".+"));
    verify(patternBuilder);
  }

  private void verifyIssueTypesSet(IssueType... issueTypes) {
    RequestPatternBuilder patternBuilder = getRequestedFor(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"));
    for (IssueType element : issueTypes) {
      patternBuilder = patternBuilder.withQueryParam("issues", containing(element.name()));
    }
    if (issueTypes.length == 0) {
      patternBuilder = patternBuilder.withQueryParam("issues", equalTo(""));
    }
    verify(patternBuilder);
  }

  private void setEnvVariables(String targetBuild, String baselineBuild, String token, String targetVersion, String baselineVersion, long slowRequestsAllowed, long exceptionsAllowed, long threshold, ComparisonStrategy strategy, EnumSet<IssueType> issueTypes) {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("appName", APP_NAME);
    env.put("targetBuild", targetBuild);
    env.put("targetVersion", targetVersion);
    env.put("baselineBuild", baselineBuild);
    env.put("baselineVersion", baselineVersion);
    env.put("apiToken", token);
    env.put("serverUrl", wireMockRule.baseUrl());
    env.put("slowRequestsAllowed", String.valueOf(slowRequestsAllowed));
    env.put("excessiveIOAllowed", String.valueOf(IGNORE_ALL_EXCESSIVE_IO_ISSUES));
    env.put("exceptionsAllowed", String.valueOf(exceptionsAllowed));
    env.put("slaGlobalLimit", String.valueOf(threshold));
    env.put("comparisonStrategy", strategy.name());
    for (IssueType element : issueTypes) {
      env.put(element.name(), "true");
    }
    j.jenkins.getGlobalNodeProperties().add(prop);
  }

  private FreeStyleProject makeDefault() throws IOException {
    return makeProject(TARGET_BUILD, BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithAllowedIssues(long slowRequestsAllowed, long exceptionsAllowed) throws IOException {
    return makeProject(TARGET_BUILD, BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, slowRequestsAllowed, exceptionsAllowed, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithThreshold(long threshold) throws IOException {
    return makeProject(TARGET_BUILD, BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, threshold, DEFAULT_BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithStrategy(ComparisonStrategy strategy) throws IOException {
    return makeProject(TARGET_BUILD, BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, strategy, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithIssueTypes(IssueType... typesArray) throws IOException {
    EnumSet<IssueType> issueTypes = typesArray.length > 0 ? EnumSet.copyOf(Arrays.asList(typesArray)) : EnumSet.noneOf(IssueType.class);
    return makeProject(TARGET_BUILD, BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE, issueTypes);
  }

  private FreeStyleProject makeWithEmptyVersions() throws IOException {
    return makeProject(TARGET_BUILD, BASELINE_BUILD, API_TOKEN, EMPTY_VERSION, EMPTY_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithEmptyApiToken() throws IOException {
    return makeProject(TARGET_BUILD, BASELINE_BUILD, EMPTY_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithEmptyTargetBuild() throws IOException {
    return makeProject(EMPTY_TARGET_BUILD, BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithEmptyMandatoryBaselineBuild() throws IOException {
    return makeProject(TARGET_BUILD, EMPTY_BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeWithEmptyOptionalBaselineBuild() throws IOException {
    return makeProject(TARGET_BUILD, EMPTY_BASELINE_BUILD, API_TOKEN, TARGET_VERSION, BASELINE_VERSION, IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST, DEFAULT_BASELINE, EnumSet.allOf(IssueType.class));
  }

  private FreeStyleProject makeProject(String targetBuild, String baselineBuild, String token, String targetVersion, String baselineVersion, long slowRequestsAllowed, long exceptionsAllowed, long threshold, ComparisonStrategy strategy, EnumSet<IssueType> issueTypes) throws IOException {
    setEnvVariables(targetBuild, baselineBuild, token, targetVersion, baselineVersion, slowRequestsAllowed, exceptionsAllowed, threshold, strategy, issueTypes);
    FreeStyleProject project = j.createFreeStyleProject();
    Publisher qrebel = new QRebelPublisher(APP_NAME, targetBuild, targetVersion, baselineBuild, baselineVersion,
        token, wireMockRule.baseUrl(), strategy.name(), slowRequestsAllowed, IGNORE_ALL_EXCESSIVE_IO_ISSUES, exceptionsAllowed, threshold,
        issueTypes.contains(IssueType.DURATION), issueTypes.contains(IssueType.IO), issueTypes.contains(IssueType.EXCEPTIONS));
    project.getPublishersList().add(qrebel);
    return project;
  }

  private Build buildAndAssertFailure(FreeStyleProject project) throws Exception {
    return j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
  }

  private Build buildAndAssertSuccess(FreeStyleProject project) throws Exception {
    return j.buildAndAssertSuccess(project);
  }
}
