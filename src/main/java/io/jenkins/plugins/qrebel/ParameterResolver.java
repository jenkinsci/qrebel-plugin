package io.jenkins.plugins.qrebel;

import java.io.IOException;
import java.util.Optional;

import hudson.EnvVars;
import hudson.model.Build;
import hudson.model.TaskListener;
import hudson.util.VariableResolver;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 */

final class ParameterResolver {

  private final VariableResolver buildVariableResolver;
  private final EnvVars envVars;

  private ParameterResolver(VariableResolver buildVariableResolver, EnvVars envVars) {
    this.buildVariableResolver = buildVariableResolver;
    this.envVars = envVars;
  }

  static ParameterResolver make(Build build, TaskListener listener) throws IOException, InterruptedException {
    return new ParameterResolver(build.getBuildVariableResolver(), build.getEnvironment(listener));
  }

  String get(String name) {
    if (!name.startsWith("$")) {
      return name;
    }

    Optional<Object> resolvedParameter = Optional.ofNullable(buildVariableResolver.resolve(name.substring(1)));

    if (resolvedParameter.isPresent()) {
      String resolvedValue = (String) resolvedParameter.get();
      if (resolvedValue.contains("$")) {
        //TODO Get the variable and query the value
        return get(resolvedValue);
      }
      else {
        return (String) resolvedParameter.get();
      }
    }
    else if (envVars.get(name) != null) {
      return envVars.get(name);
    }
    else if (System.getenv(name) != null) {
      return System.getenv(name);
    }

    throw new IllegalArgumentException(String.format("Parameter %s is not set", name));
  }
}
