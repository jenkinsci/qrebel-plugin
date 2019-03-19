/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Which issue types to show
@AllArgsConstructor
enum IssueType {
  DURATION("DURATION"), IO("IO"), EXCEPTIONS("EXCEPTIONS");

  @Getter
  private final String name;
}
