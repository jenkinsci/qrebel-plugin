package io.jenkins.plugins.qrebel;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

public class QRebelData {
  private final String appName;
  private final String target;
  private final String viewUrl;
  private final JSONObject issues;
  private final JSONArray entryPoints;

  private QRebelData(String appName, String target, JSONObject issues, JSONArray entryPoints, String viewUrl) {
    this.appName = appName;
    this.target = target;
    this.issues = issues;
    this.entryPoints = entryPoints;
    this.viewUrl = viewUrl;
  }

  public static QRebelData parse(String json) {
    JSONObject jsonObject = (JSONObject) JSONValue.parse(json);

    String appName = (String) jsonObject.get("appName");
    String target = (String) jsonObject.get("target");
    String viewUrl = (String) jsonObject.get("appViewUrl");
    JSONObject issues = (JSONObject) jsonObject.get("issuesCount");
    JSONArray entryPoints = (JSONArray) jsonObject.get("entryPoints");

    return new QRebelData(appName, target, issues, entryPoints, viewUrl);
  }

  public Long getExceptionCount() {
    return parseIssueCount("EXCEPTIONS");
  }

  public Long getDurationCount() {
    return parseIssueCount("DURATION");
  }

  public Long getIOCount() {
    return parseIssueCount("IO");
  }

  public String getAppName() {
    return appName;
  }

  public String getTarget() {
    return target;
  }

  public String getViewUrl() {
    return viewUrl;
  }

  public Optional<List<Long>> getEntryPointTimes() {
    List<JSONObject> durations = (List<JSONObject>) entryPoints.stream()
        .map(entry -> ((JSONObject) entry)
            .get("duration"))
        .filter(e -> e != null)
        .collect(Collectors.toList());

    if (durations.size() > 0) {
      List<Long> slowestPercentile = durations.stream()
          .map(entry -> ((Long) entry.get("slowestPercentile")))
          .collect(Collectors.toList());
      return Optional.of(slowestPercentile);
    }

    return Optional.empty();
  }

  public Optional<List<String>> getEntryPointNames() {
    List<String> entryPointNames = (List<String>) entryPoints.stream()
        .map(e -> ((JSONObject) e).get("name"))
        .collect(Collectors.toList());

    return Optional.of(entryPointNames);
  }

  private Long parseIssueCount(String name) {
    Long value = (Long) issues.get(name);
    return value != null ? value : 0;
  }
}
