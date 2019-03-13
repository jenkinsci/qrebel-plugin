package io.jenkins.plugins.qrebel;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
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
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Post-build step that keeps configuration. It can be persisted by XStream, so all the non-serializable fields are
 * kept in QRebelStepPerformer.
 */
@Data
@EqualsAndHashCode(callSuper=true)
public class QRebelPublisher extends Recorder implements SimpleBuildStep {

  static final String PLUGIN_SHORT_NAME = "qrebel";

  final String appName;
  final String targetBuild;
  final String targetVersion;
  final String baselineBuild;
  final String baselineVersion;
  final String apiKey;
  final String serverUrl;
  final int durationFail;
  final int ioFail;
  final int exceptionFail;
  final int threshold;

  @DataBoundConstructor
  public QRebelPublisher(String appName, String targetBuild, String targetVersion, String baselineBuild, String baselineVersion, String apiKey, String serverUrl, int durationFail, int ioFail, int exceptionFail, int threshold) {
    this.appName = appName;
    this.targetBuild = targetBuild;
    this.targetVersion = targetVersion;
    this.baselineBuild = baselineBuild;
    this.baselineVersion = baselineVersion;
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

  @Symbol(PLUGIN_SHORT_NAME)
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
