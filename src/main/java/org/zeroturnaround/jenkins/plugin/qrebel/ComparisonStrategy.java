/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Comparison strategy is used in regression detection
@AllArgsConstructor
enum ComparisonStrategy {
  BASELINE("BASELINE"), DEFAULT_BASELINE("DEFAULT_BASELINE"), THRESHOLD("THRESHOLD");

  private static final Map<String, ComparisonStrategy> map = unmodifiableMap(Stream.of(values()).collect(toMap(ComparisonStrategy::name, identity())));

  @Getter
  private final String name;

  static ComparisonStrategy get(String name) {
    return map.get(name);
  }
}
