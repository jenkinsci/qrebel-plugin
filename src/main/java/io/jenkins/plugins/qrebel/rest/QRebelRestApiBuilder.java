package io.jenkins.plugins.qrebel.rest;


import java.io.IOException;
import org.apache.commons.io.IOUtils;

import feign.Feign;
import feign.Response;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Configures Open FEIGN
 */
public class QRebelRestApiBuilder {
  // build a new class instance
  public static QRebelRestApi make(String serverUrl) {
    return Feign.builder()
        .errorDecoder(new ErrorBodyDecoder())
        .encoder(new GsonEncoder())
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
