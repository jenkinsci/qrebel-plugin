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
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Post-build step that keeps configuration. It can be persisted by XStream, so all the non-serializable fields are
 * kept in QRebelStepPerformer.
 */
@Value
@EqualsAndHashCode(callSuper=true)
public class QRebelPublisher extends Recorder implements SimpleBuildStep {

  private final String appName;
  private final String targetBuildName;
  private final String targetBuildVersion;
  private final String baselineBuildName;
  private final String baselineBuildVersion;
  private final String apiKey;
  private final String serverUrl;
  private final int durationFail;
  private final int ioFail;
  private final int exceptionFail;
  private final int threshold;

  @DataBoundConstructor
  public QRebelPublisher(String appName, String targetBuildName, String targetBuildVersion, String baselineBuildName, String baselineBuildVersion, String apiKey, String serverUrl, int durationFail,
                         int ioFail, int exceptionFail, int threshold) {
    this.appName = appName;
    this.targetBuildName = targetBuildName;
    this.targetBuildVersion = targetBuildVersion;
    this.baselineBuildName = baselineBuildName;
    this.baselineBuildVersion = baselineBuildVersion;
    this.apiKey = apiKey;
    this.serverUrl = serverUrl;
    this.durationFail = durationFail;
    this.ioFail = ioFail;
    this.exceptionFail = exceptionFail;
    this.threshold = threshold;
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException {
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
