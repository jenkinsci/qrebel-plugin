/*
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */
package org.zeroturnaround.jenkins.plugin.qrebel.rest;

import java.io.IOException;
import java.io.PrintStream;
import org.apache.commons.io.IOUtils;

import feign.Feign;
import feign.Logger;
import feign.Response;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;

/**
 * Configures Open FEIGN
 */
public class QRebelRestApiClient {
  // create a new client instance
  public static QRebelRestApi create(String apiUrl, PrintStream logger) {
    return Feign.builder()
        .errorDecoder(new ErrorBodyDecoder())
        .logLevel(Logger.Level.BASIC)
        .logger(new Logger() {
          @Override
          protected void log(String configKey, String format, Object... args) {
            logger.format(methodTag(configKey) + format + "%n", args);
          }
        })
        .decoder(new GsonDecoder())
        .target(QRebelRestApi.class, apiUrl);
  }

  // create a new client instance without logging and JSON parsing
  public static QRebelRestApi createBasic(String apiUrl) {
    return Feign.builder().target(QRebelRestApi.class, apiUrl);
  }

  // translate known issues or extract response body otherwise
  private static class ErrorBodyDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
      return new IllegalStateException(responseToString(response));
    }
  }

  private static String responseToString(Response response) {
    try {
      return IOUtils.toString(response.body().asInputStream());
    }
    catch (IOException e) {
      return response.toString();
    }
  }
}
