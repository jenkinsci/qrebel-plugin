/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel.rest;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IssuesCount {
  public final long DURATION;
  public final long EXCEPTIONS;
  public final long IO;
}
