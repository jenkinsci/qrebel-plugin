package io.jenkins.plugins.qrebel;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Issues data parsed from JSON
 */

@RequiredArgsConstructor
class Issues {

  @RequiredArgsConstructor
  static class IssuesCount {
    final long DURATION;
    final long EXCEPTIONS;
    final long IO;
  }

  @RequiredArgsConstructor
  static class EntryPoint {
    final Duration duration;
  }

  @RequiredArgsConstructor
  static class Duration {
    final Long slowestPercentile;
  }

  final String appName;
  final String baselineBuild;
  final String baselineVersion;
  final String targetBuild;
  final String targetVersion;
  final String appViewUrl;
  final IssuesCount issuesCount;
  final List<EntryPoint> entryPoints;
}
