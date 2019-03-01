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
import hudson.slaves.EnvironmentVariablesNodeProperty;

public class QRebelTestPublisherTest {

  @Rule
  public JenkinsRule j = new JenkinsRule();
  @ClassRule
  public static BuildWatcher bw = new BuildWatcher();
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

  private static final String APP_NAME = "foo";
  private static final String TARGET_BUIKD = "2.0.6RC3";
  private static final String BASELINE_BUIKD = "2.05RC1";

  private static final String CORRECT_AUTH_KEY = "correct-key";

  private static final int DURATION_FAIL = 0;
  private static final int IO_FAIL = 0;
  private static final int EXCEPTION_FAIL = 0;
  private static final int THRESHOLD = 100;


  @Test
  public void testFlow() throws Exception {
    stubAuthApi(CORRECT_AUTH_KEY, ok());
    stubIssuesApi(CORRECT_AUTH_KEY, TARGET_BUIKD, ok());
    j.buildAndAssertSuccess(makeProject(APP_NAME, TARGET_BUIKD, BASELINE_BUIKD, CORRECT_AUTH_KEY, wireMockRule.baseUrl(), DURATION_FAIL, IO_FAIL, EXCEPTION_FAIL, THRESHOLD));
    verify(putRequestedFor(urlEqualTo("/api/applications/" + APP_NAME + "/baselines/default/"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo(CORRECT_AUTH_KEY))
    );
    verify(getRequestedFor(urlEqualTo("/api/applications/" + APP_NAME + "/issues/?targetBuild=" + TARGET_BUIKD + "&defaultBaseline"))
        .withHeader("authorization", equalTo(CORRECT_AUTH_KEY))
    );
  }

  private void stubAuthApi(String authKey, ResponseDefinitionBuilder response) {
    stubFor(put("/api/applications/" + APP_NAME + "/baselines/default/")
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo(authKey))
        .willReturn(response));
  }

  private void stubIssuesApi(String authKey, String targetBuild, ResponseDefinitionBuilder response) throws IOException {
    String issuesJson = IOUtils.toString(this.getClass().getResourceAsStream("issues.json"));
    stubFor(get("/api/applications/" + APP_NAME + "/issues/?targetBuild=" + targetBuild + "&defaultBaseline")
        .withHeader("authorization", equalTo(authKey))
        .willReturn(response.withBody(issuesJson)));
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
}
