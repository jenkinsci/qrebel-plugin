/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import java.util.stream.LongStream;

import org.zeroturnaround.jenkins.plugin.qrebel.rest.Issues;

import lombok.Value;

@Value
class IssuesStats {

  private final Issues qRData;

  // check if found issues are too slow
  boolean isThresholdProvidedAndExceeded(long threshold) {
    return threshold > 0L && threshold <= getSlowestDuration();
  }

  long getSlowestDuration() {
    return getDurationsAsStream().max().orElse(0L);
  }

  private LongStream getDurationsAsStream() {
    return qRData.entryPoints
        .stream()
        .filter(entryPoint -> entryPoint.duration != null && entryPoint.duration.slowestPercentile != null)
        .mapToLong(entryPoint -> entryPoint.duration.slowestPercentile);
  }
}
