package io.jenkins.plugins.qrebel.rest;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
import lombok.Data;

@Data
public class BuildClassifier {
  public final String build;
  public final String version;
}
