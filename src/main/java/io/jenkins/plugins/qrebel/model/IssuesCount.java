package io.jenkins.plugins.qrebel.model;

import lombok.Value;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Issues counts parsed from JSON
 */

@Value
public class IssuesCount {
  private final long DURATION;
  private final long EXCEPTIONS;
  private final long IO;
}