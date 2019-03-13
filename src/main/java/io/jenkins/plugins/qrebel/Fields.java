package io.jenkins.plugins.qrebel;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

import lombok.Builder;

/**
 * Plugin configuration
 */
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
