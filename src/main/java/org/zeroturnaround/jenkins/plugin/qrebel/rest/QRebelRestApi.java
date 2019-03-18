/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel.rest;

import feign.Headers;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;

/**
 * Rest API for feign HTTP client
 */
@Headers({"Content-Type: application/json", "authorization: {apiKey}"})
public interface QRebelRestApi {

  @RequestLine("GET /api/applications/{appName}/issues/")
  Issues getIssues(@Param("apiKey") String apiKey, @Param("appName") String appName, @QueryMap IssuesRequest request);
}
