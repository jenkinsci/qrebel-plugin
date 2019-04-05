/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel.rest;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Wither;

/**
 * Issues data parsed from JSON
 */
@RequiredArgsConstructor
@Wither
public class IssuesResponse {

  public final String appName;
  public final String baselineBuild;
  public final String baselineVersion;
  public final String targetBuild;
  public final String targetVersion;
  public final String appViewUrl;
  public final IssuesCount issuesCount;
  public final List<EntryPoint> entryPoints;

}
