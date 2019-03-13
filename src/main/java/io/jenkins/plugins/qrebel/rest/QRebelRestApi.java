package io.jenkins.plugins.qrebel.rest;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Rest API for feign HTTP client
 */

import feign.Headers;
import feign.Param;
import feign.RequestLine;

/**
 * Rest API for feign HTTP client
 */
@Headers({"Content-Type: application/json", "authorization: {apiKey}"})
public interface QRebelRestApi {

  @RequestLine("PUT /api/applications/{appName}/baselines/default/")
  void setDefaultBaseline(@Param("apiKey") String apiKey, @Param("appName") String appName, BuildClassifier classifier);

  @RequestLine("GET /api/applications/{appName}/issues/?targetBuild={targetBuild}&targetVersion={targetVersion}&slowRequestsAllowed={slowRequestsAllowed}&excessiveIOAllowed={excessiveIOAllowed}&exceptionsAllowed={exceptionsAllowed}&jenkinsPluginVersion={jenkinsPluginVersion}&defaultBaseline")
  Issues getIssuesVsBaseline(@Param("apiKey") String apiKey, @Param("appName") String appName, @Param("targetBuild") String targetBuild,
                             @Param("targetVersion") String targetVersion, @Param("slowRequestsAllowed") int slowRequestsAllowed,
                             @Param("excessiveIOAllowed") int excessiveIOAllowed, @Param("exceptionsAllowed") int exceptionsAllowed,
                             @Param("jenkinsPluginVersion") String jenkinsPluginVersion);

  @RequestLine("GET /api/applications/{appName}/issues/?targetBuild={targetBuild}&targetVersion={targetVersion}&slowRequestsAllowed={slowRequestsAllowed}&excessiveIOAllowed={excessiveIOAllowed}&exceptionsAllowed={exceptionsAllowed}&jenkinsPluginVersion={jenkinsPluginVersion}x")
  Issues getIssuesVsThreshold(@Param("apiKey") String apiKey, @Param("appName") String appName, @Param("targetBuild") String targetBuild,
                              @Param("targetVersion") String targetVersion, @Param("slowRequestsAllowed") int slowRequestsAllowed,
                              @Param("excessiveIOAllowed") int excessiveIOAllowed, @Param("exceptionsAllowed") int exceptionsAllowed,
                              @Param("jenkinsPluginVersion") String jenkinsPluginVersion);
}
