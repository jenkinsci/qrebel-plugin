package io.jenkins.plugins.qrebel;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;

public class QRebelTestPublisherTest {

  @Rule
  public JenkinsRule j = new JenkinsRule();
  @ClassRule
  public static BuildWatcher bw = new BuildWatcher();
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

  private static final String APP_NAME = "foo";
  private static final String TARGET_BUILD = "2.0.6RC3";
  private static final String BASELINE_BUIKD = "2.05RC1";

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
    stubAuthApi(AUTH_KEY, forbidden());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyBaselineCalled(AUTH_KEY);
  }

  @Test
  public void authFailedOnIssues() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, forbidden());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyIssuesCalled(AUTH_KEY, TARGET_BUILD);
  }

  @Test
  public void tooManySlowRequests() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, ok());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), TOO_MANY_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
  }

  @Test
  public void tooManyExceptions() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, ok());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, TOO_MANY_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
  }

  @Test
  public void thresholdBelowFastest() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, ok());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, THRESHOLD_BELOW_FASTEST));
  }

  @Test
  public void thresholdTouchesFastest() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, ok());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, FASTEST_REQUEST));
  }

  @Test
  public void thresholdTouchesSlowest() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, ok());
    buildAndAssertFailure(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, SLOWEST_REQUEST));
  }

  @Test
  public void thresholdAboveSlowest() throws Exception {
    stubAuthApi(AUTH_KEY, ok());
    stubIssuesApi(AUTH_KEY, TARGET_BUILD, ok());
    j.buildAndAssertSuccess(makeProject(APP_NAME, TARGET_BUILD, BASELINE_BUIKD, AUTH_KEY, wireMockRule.baseUrl(), IGNORE_ALL_SLOW_REQUESTS, IGNORE_ALL_IO_ISSUES, IGNORE_ALL_EXCEPTIONS, THRESHOLD_ABOVE_SLOWEST));
    verifyBaselineCalled(AUTH_KEY);
    verifyIssuesCalled(AUTH_KEY, TARGET_BUILD);
  }

  private void stubAuthApi(String authKey, ResponseDefinitionBuilder response) {
    stubFor(put("/api/applications/" + APP_NAME + "/baselines/default/")
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo(authKey))
        .willReturn(response));
  }

  private void verifyBaselineCalled(String authKey) {
    verify(putRequestedFor(urlEqualTo("/api/applications/" + APP_NAME + "/baselines/default/"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo(authKey))
    );
  }

  private void stubIssuesApi(String authKey, String targetBuild, ResponseDefinitionBuilder response) throws IOException {
    String issuesJson = IOUtils.toString(this.getClass().getResourceAsStream("issues.json"));
    stubFor(get("/api/applications/" + APP_NAME + "/issues/?targetBuild=" + targetBuild + "&defaultBaseline")
        .withHeader("authorization", equalTo(authKey))
        .willReturn(response.withBody(issuesJson)));
  }

  private void verifyIssuesCalled(String authKey, String targetBuild) throws IOException {
    verify(getRequestedFor(urlEqualTo("/api/applications/" + APP_NAME + "/issues/?targetBuild=" + targetBuild + "&defaultBaseline"))
        .withHeader("authorization", equalTo(authKey))
    );
  }

  private void setEnvVariables(String appName, String targetBuild, String baselineBuild, String apiKey, String serverUrl, int durationFail, int ioFail, int exceptionFail, int threshold) {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("appName", appName);
    env.put("targetBuild", targetBuild);
    env.put("baselineBuild", baselineBuild);
    env.put("apiKey", apiKey);
    env.put("serverUrl", serverUrl);
    env.put("durationFail", String.valueOf(durationFail));
    env.put("ioFail", String.valueOf(ioFail));
    env.put("exceptionFail", String.valueOf(exceptionFail));
    env.put("threshold", String.valueOf(threshold));
    j.jenkins.getGlobalNodeProperties().add(prop);
  }

  private FreeStyleProject makeProject(String appName, String targetBuild, String baselineBuild, String apiKey, String serverUrl, int durationFail, int ioFail, int exceptionFail, int threshold) throws IOException {
    setEnvVariables(appName, targetBuild, baselineBuild, apiKey, serverUrl, durationFail, ioFail, exceptionFail, threshold);
    FreeStyleProject project = j.createFreeStyleProject();
    project.getPublishersList().add(new QRebelPublisher(appName, targetBuild, baselineBuild, apiKey, serverUrl, durationFail, ioFail, exceptionFail, threshold));
    return project;
  }

  private void buildAndAssertFailure(FreeStyleProject project) throws Exception {
    j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
  }
}
