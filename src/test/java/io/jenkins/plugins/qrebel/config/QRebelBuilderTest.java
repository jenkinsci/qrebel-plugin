package io.jenkins.plugins.qrebel.config;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.qrebel.QRebelBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class QRebelBuilderTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private final String url = "ahoj";
    private final String entryPoints = "";
    private String target = "1.1.2";
    private String baseline = "1.1.0";

    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        String appName = "PetClinic";
        project.getBuildersList().add(new QRebelBuilder(url, appName, target, baseline, entryPoints,
                0, 0, 0));
        project = jenkins.configRoundtrip(project);

        jenkins.assertEqualDataBoundBeans(new QRebelBuilder(url, appName, target, baseline, entryPoints,
                0,0, 0), project.getBuildersList().get(0));
    }
}