/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package io.jenkins.plugins.qrebel.rest;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Issues data parsed from JSON
 */
@RequiredArgsConstructor
public class Issues {

  @RequiredArgsConstructor
  public static class IssuesCount {
    public final long DURATION;
    public final long EXCEPTIONS;
    public final long IO;
  }

  @RequiredArgsConstructor
  public static class EntryPoint {
    public final Duration duration;
  }

  @RequiredArgsConstructor
  public static class Duration {
    public final Long slowestPercentile;
  }

  public final String appName;
  public final String baselineBuild;
  public final String baselineVersion;
  public final String targetBuild;
  public final String targetVersion;
  public final String appViewUrl;
  public final IssuesCount issuesCount;
  public final List<EntryPoint> entryPoints;
}
