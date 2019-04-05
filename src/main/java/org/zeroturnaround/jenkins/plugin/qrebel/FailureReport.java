/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.zeroturnaround.jenkins.plugin.qrebel.rest.IssuesResponse;

import lombok.Value;

@Value
class FailureReport {
  private final List<String> lines;

  static FailureReport generate(IssuesResponse qRData, Fields fields) {
    List<String> lines = new ArrayList<>();
    if (qRData.issuesCount.DURATION > fields.slowRequestsAllowed  || qRData.issuesCount.IO > fields.excessiveIoAllowed || qRData.issuesCount.EXCEPTIONS > fields.exceptionsAllowed) {
      lines.add(String.format("Build failed because QRebel found regressions in %s", qRData.appName));
    }
    else {
      lines.add(String.format("Build failed by QRebel Plugin because Performance Gate thresholds were exceeded in %s", qRData.appName));
    }

    lines.add("TARGET");
    addBuildLines(lines, qRData.targetBuild, qRData.targetVersion);

    if (fields.comparisonStrategy != ComparisonStrategy.THRESHOLD) {
      lines.add("BASELINE");
      addBuildLines(lines, qRData.baselineBuild, qRData.baselineVersion);
    }

    lines.add(String.format("Slow Requests: %d", qRData.issuesCount.DURATION));
    lines.add(String.format("Excessive IO: %d", qRData.issuesCount.IO));
    lines.add(String.format("Exceptions: %d", qRData.issuesCount.EXCEPTIONS));

    if (fields.slaGlobalLimit > 0) {
      lines.add(String.format("SLA global limit (ms): %d ms | slowest endpoint time(ms): %d ms", fields.slaGlobalLimit, new IssuesStats(qRData).getSlowestDuration()));
    }
    lines.add(String.format("For full report check your <a href= %s >dashboard</a>.", qRData.appViewUrl));

    return new FailureReport(lines);
  }

  private static void addBuildLines(List<String> lines, String build, String version) {
    lines.add(String.format(" build: %s", build));
    if (StringUtils.isNotBlank(version)) {
      lines.add(String.format(" version: %s", version));
    }
  }

  String asHtml() {
    return StringUtils.join(lines, "<br/>%n");
  }

  String asText() {
    return StringUtils.join(lines, "\n");
  }
}
