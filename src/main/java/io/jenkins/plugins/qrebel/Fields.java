package io.jenkins.plugins.qrebel;

import lombok.Builder;

@Builder
class Fields {
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
}
