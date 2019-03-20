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
  public static QRebelRestApi create(String serverUrl, PrintStream logger) {
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
        .target(QRebelRestApi.class, serverUrl);
  }

  // extract response body if HTTP request fails
  private static class ErrorBodyDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
      try {
        return new IllegalStateException(IOUtils.toString(response.body().asInputStream()));
      }
      catch (IOException e) {
        return new IllegalStateException(response.toString(), e);
      }
    }
  }
}
