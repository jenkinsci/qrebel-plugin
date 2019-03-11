package io.jenkins.plugins.qrebel;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import lombok.Value;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
@Value
class IssuesStats {

  private final Issues qRData;

  // check if found issues are too slow
  boolean isThresholdProvidedAndExceeded(int threshold) {
    Optional<List<Long>> entryPointTimes = getEntryPointTimes();
    if (!entryPointTimes.isPresent()) {
      return false;
    }

    OptionalLong maxDelayTime = entryPointTimes.get().stream().mapToLong(v -> v).max();
    if (maxDelayTime.isPresent()) {
      return threshold > 0 && threshold <= (int) maxDelayTime.getAsLong();
    }
    return false;
  }

  int getSlowestDelay() {
    Optional<List<Long>> entryPointTimes = getEntryPointTimes();
    if (!entryPointTimes.isPresent()) {
      return 0;
    }

    OptionalLong maxDelayTime = entryPointTimes.get().stream().mapToLong(v -> v).max();
    if (maxDelayTime.isPresent()) {
      return (int) maxDelayTime.getAsLong();
    }
    return 0;
  }

  private Optional<List<Long>> getEntryPointTimes() {
    List<Long> slowestPercentile = qRData.entryPoints
        .stream()
        .filter(entryPoint -> entryPoint.duration != null && entryPoint.duration.slowestPercentile != null)
        .map(entryPoint -> entryPoint.duration.slowestPercentile)
        .collect(Collectors.toList());
    return slowestPercentile.isEmpty() ?
        Optional.empty() : Optional.of(slowestPercentile);
  }
}
