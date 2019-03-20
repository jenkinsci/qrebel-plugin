/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.experimental.Wither;

/**
 * Plugin configuration
 */
@Builder
@Wither
@SuppressFBWarnings(justification = "Generated code")
class Fields {
  final String appName;
  final String targetBuild;
  final String targetVersion;
  final String baselineBuild;
  final String baselineVersion;
  final String apiToken;
  final String serverUrl;
  final ComparisonStrategy comparisonStrategy;
  final long slowRequestsAllowed;
  final long excessiveIoAllowed;
  final long exceptionsAllowed;
  final long slaGlobalLimit;
  final String issueTypes;
}
