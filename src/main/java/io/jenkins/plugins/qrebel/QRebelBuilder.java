package io.jenkins.plugins.qrebel;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 *
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

public class QRebelBuilder extends Builder implements SimpleBuildStep {

    private static final String QREBEL_URL = "https://hub.qrebel.com";

    private final String appName;
    private final String targetBuild;
    private final String baselineBuild;
    private final String apiKey;
    private final String serverUrl;
    public int durationFail;
    public int ioFail;
    public int exceptionFail;
    public int threshold;

    @DataBoundConstructor
    public QRebelBuilder(String appName, String targetBuild, String baselineBuild, String apiKey, String serverUrl, int durationFail,
                         int ioFail, int exceptionFail, int threshold) {
        this.appName = appName;
        this.targetBuild = targetBuild;
        this.baselineBuild = baselineBuild;
        this.apiKey = apiKey;
        this.serverUrl = serverUrl;
        this.durationFail = durationFail;
        this.ioFail = ioFail;
        this.exceptionFail = exceptionFail;
        this.threshold = threshold;
    }

    public String getServelUrl() {
      return StringUtils.isBlank(serverUrl)? QREBEL_URL : serverUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAppName() {
        return appName;
    }

    public String getTargetBuild() {
        return targetBuild;
    }

    public String getBaselineBuild() {
        return baselineBuild;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        QRebelStepPerformer logic = QRebelStepPerformer.make(this, run, listener);
        logic.perform();
    }

    @Symbol("qrebel")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public @Nonnull String getDisplayName() {
            return Messages.QRebelBuilder_DescriptorImpl_DisplayName();
        }
    }
}
