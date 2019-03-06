package io.jenkins.plugins.qrebel.model;

import javax.annotation.Nullable;

import lombok.Value;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Build data parsed from JSON
 */

@Value
public class BuildClassifier {
  final String name;
  @Nullable
  final String version;
}
