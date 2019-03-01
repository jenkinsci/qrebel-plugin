package io.jenkins.plugins.qrebel;

import javax.annotation.Nonnull;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

public class QRebelPublisher extends Recorder implements SimpleBuildStep {

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
  public QRebelPublisher(String appName, String targetBuild, String baselineBuild, String apiKey, String serverUrl, int durationFail,
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
    return serverUrl;
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
  public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public @Nonnull
    String getDisplayName() {
      return Messages.QRebelBuilder_DescriptorImpl_DisplayName();
    }
  }
}
