package io.jenkins.plugins.qrebel.model;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Value;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Issues data parsed from JSON
 */

@Value
public class Issues {

  private final String appName;
  private final BuildClassifier target;
  private final String appViewUrl;
  private final IssuesCount issuesCount;
  private final List<EntryPoint> entryPoints;

  public Optional<List<Long>> getEntryPointTimes() {
    List<Long> slowestPercentile = getEntryPoints()
        .stream()
        .filter(entryPoint -> entryPoint.getDuration() != null && entryPoint.getDuration().getSlowestPercentile() != null)
        .map(entryPoint -> entryPoint.getDuration().getSlowestPercentile())
        .collect(Collectors.toList());
    return slowestPercentile.isEmpty() ?
        Optional.empty() : Optional.of(slowestPercentile);
  }
}
