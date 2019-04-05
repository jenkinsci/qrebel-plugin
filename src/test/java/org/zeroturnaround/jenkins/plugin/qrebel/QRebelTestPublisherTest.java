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
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.IssuesResponse;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hudson.model.Build;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
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
  public JenkinsRule j = new JenkinsRule();
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

  @Test
  public void authFailedOnIssues() throws Exception {
    stubIssuesApi(forbidden());
    buildAndAssertFailure(withDefault());
    verifyIssuesCalled();
  }

  @Test
  public void tooManySlowRequests() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(withDefault().withSlowRequestsAllowed(TOO_MANY_SLOW_REQUESTS).withExceptionsAllowed(IGNORE_ALL_EXCEPTIONS));
  }

  @Test
  public void tooManyExceptions() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(withDefault().withSlowRequestsAllowed(IGNORE_ALL_SLOW_REQUESTS).withExceptionsAllowed(TOO_MANY_EXCEPTIONS));
  }

  @Test
  public void limitBelowFastest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(withDefault().withSlaGlobalLimit(GLOBAL_LIMIT_BELOW_FASTEST));
  }

  @Test
  public void limitTouchesFastest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(withDefault().withSlaGlobalLimit(FASTEST_REQUEST));
  }

  @Test
  public void limitTouchesSlowest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertFailure(withDefault().withSlaGlobalLimit(SLOWEST_REQUEST));
  }

  @Test
  public void limitAboveSlowest() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withSlaGlobalLimit(GLOBAL_LIMIT_ABOVE_SLOWEST));
    verifyIssuesCalled();
  }

  @Test
  public void baselineStrategySet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withComparisonStrategy(BASELINE.name()));
    verifyStrategySet(BASELINE);
  }

  @Test
  public void globalThresholdStrategySet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withComparisonStrategy(THRESHOLD.name()));
    verifyStrategySet(THRESHOLD);
  }

  @Test
  public void defaultBaselineStrategySet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withComparisonStrategy(DEFAULT_BASELINE.name()));
    verifyStrategySet(DEFAULT_BASELINE);
  }

  @Test
  public void userLimitsSet() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault());
    verifyLimitsAndProtocolSet();
  }

  @Test
  public void noIssueTypes() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withDURATION(false).withIO(false).withEXCEPTIONS(false));
    verifyIssueTypesSet();
  }

  @Test
  public void allIssueTypes() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withDURATION(true).withIO(true).withEXCEPTIONS(true));
    verifyIssueTypesSet(IssueType.EXCEPTIONS, IssueType.DURATION, IssueType.IO);
  }

  @Test
  public void onlyDurationIssueType() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withDURATION(true).withIO(false).withEXCEPTIONS(false));
    verifyIssueTypesSet(IssueType.DURATION);
  }

  @Test
  public void onlyIoIssueType() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withDURATION(false).withIO(true).withEXCEPTIONS(false));
    verifyIssueTypesSet(IssueType.IO);
  }

  @Test
  public void onlyExceptionIssueType() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withDURATION(false).withIO(false).withEXCEPTIONS(true));
    verifyIssueTypesSet(IssueType.EXCEPTIONS);
  }

  @Test
  public void emptyVersions() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    buildAndAssertSuccess(withDefault().withBaselineVersion(EMPTY_VERSION).withTargetVersion(EMPTY_VERSION));
    verifyEmptyVersions();
  }

  @Test
  public void jsonErrorShownInLogs() throws Exception {
    stubIssuesApi(400, "{ error: 'You shall not pass!'}");
    Build build = buildAndAssertFailure(withDefault());
    j.assertLogContains("You shall not pass!", build);
  }

  @Test
  public void nonJsonErrorShownInLogs() throws Exception {
    stubIssuesApi(400, "A message");
    Build build = buildAndAssertFailure(withDefault());
    j.assertLogContains("A message", build);
  }

  @Test
  public void connectionParamsCannotBeBlank() throws Exception {
    Build build = buildAndAssertFailure(withDefault().withApiToken(EMPTY_TOKEN));
    j.assertLogContains("Connection parameters cannot be blank", build);
  }

  @Test
  public void targetBuildCannotBeBlank() throws Exception {
    Build build = buildAndAssertFailure(withDefault().withTargetBuild(EMPTY_TARGET_BUILD));
    j.assertLogContains("Target build name cannot be blank", build);
  }

  @Test
  public void baselineBuildCannotBeBlankInBaseline() throws Exception {
    Build build = buildAndAssertFailure(withDefault().withComparisonStrategy(BASELINE.name()).withBaselineBuild(EMPTY_BASELINE_BUILD));
    j.assertLogContains("Baseline build name cannot be blank", build);
  }

  @Test
  public void baselineBuildCanBeBlank() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertSuccess(withDefault().withComparisonStrategy(DEFAULT_BASELINE.name()).withBaselineBuild(EMPTY_BASELINE_BUILD));
    j.assertLogNotContains("Baseline build name cannot be blank", build);
  }

  @Test
  public void buildAndVersionLogged() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertFailure(withDefault().withSlaGlobalLimit(FASTEST_REQUEST).withComparisonStrategy(THRESHOLD.name()));
    j.assertLogContains(" build: 2.0.6RC3", build);
    j.assertLogContains(" version: 1", build);
  }

  @Test
  public void blankVersionNotLogged() throws Exception {
    stubIssuesApi(ok().withBody(setResponseVersions(getIssuesJson(), EMPTY_VERSION, EMPTY_VERSION)));
    Build build = buildAndAssertFailure(withDefault().withSlaGlobalLimit(FASTEST_REQUEST).withBaselineVersion(EMPTY_VERSION).withTargetVersion(EMPTY_VERSION));
    j.assertLogNotContains(" version: ", build);
  }

  @Test
  public void slaFailureLogged() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertFailure(withDefault().withSlaGlobalLimit(GLOBAL_LIMIT_BELOW_FASTEST));
    j.assertLogContains("Build failed by QRebel Plugin because Performance Gate thresholds were exceeded in", build);
  }

  @Test
  public void baselineStrategyLogsBaselineBuild() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertFailure(withDefault().withSlowRequestsAllowed(TOO_MANY_SLOW_REQUESTS).withComparisonStrategy(BASELINE.name()));
    j.assertLogContains("BASELINE", build);
  }

  @Test
  public void thresholdStrategySkipsBaselineBuild() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertFailure(withDefault().withSlowRequestsAllowed(TOO_MANY_SLOW_REQUESTS).withComparisonStrategy(THRESHOLD.name()));
    j.assertLogNotContains("BASELINE", build);
  }

  @Test
  public void regressionLogged() throws Exception {
    stubIssuesApi(ok().withBody(getIssuesJson()));
    Build build = buildAndAssertFailure(withDefault().withSlowRequestsAllowed(TOO_MANY_SLOW_REQUESTS));
    j.assertLogContains("Build failed because QRebel found regressions ", build);
  }

  private void stubIssuesApi(ResponseDefinitionBuilder response) {
    stubFor(get(urlMatching("/api/applications/" + APP_NAME + "/issues/.*"))
        .withHeader("authorization", equalTo(API_TOKEN))
        .withQueryParam("targetBuild", equalTo(TARGET_BUILD))
        .willReturn(response));
  }

  private void stubIssuesApi(int status, String responseBody) {
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

  private String setResponseVersions(String json, String baselineVersion, String targetVersion) {
    Gson gson = new GsonBuilder().create();
    IssuesResponse element = gson
        .fromJson(json, IssuesResponse.class)
        .withBaselineVersion(baselineVersion)
        .withTargetVersion(targetVersion);
    return gson.toJson(element);
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

  private QRebelPublisher withDefault() {
    return new QRebelPublisher(APP_NAME, TARGET_BUILD, TARGET_VERSION, BASELINE_BUILD, BASELINE_VERSION,
        API_TOKEN, wireMockRule.baseUrl() + "/api", DEFAULT_BASELINE.name(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_EXCESSIVE_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, GLOBAL_LIMIT_ABOVE_SLOWEST,
        true, true, true);
  }

  private Build buildAndAssertFailure(Publisher publisher) throws Exception {
    FreeStyleProject project = j.createFreeStyleProject();
    project.getPublishersList().add(publisher);
    return j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
  }

  private Build buildAndAssertSuccess(Publisher publisher) throws Exception {
    FreeStyleProject project = j.createFreeStyleProject();
    project.getPublishersList().add(publisher);
    return j.buildAndAssertSuccess(project);
  }
}
