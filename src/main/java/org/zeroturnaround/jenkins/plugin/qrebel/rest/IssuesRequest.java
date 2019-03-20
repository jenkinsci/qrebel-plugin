/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel.rest;

import lombok.Builder;
import lombok.NonNull;

@Builder
public class IssuesRequest {
  @NonNull
  final String targetBuild;
  final String targetVersion;
  @NonNull
  final Long slowRequestsAllowed;
  @NonNull
  final Long excessiveIOAllowed;
  @NonNull
  final Long exceptionsAllowed;
  @NonNull
  final String jenkinsPluginVersion;
  final String defaultBaseline;
  final String baselineBuild;
  final String baselineVersion;
  final String issues;
}
