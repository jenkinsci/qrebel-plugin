package io.jenkins.plugins.qrebel;

import hudson.Plugin;
import jenkins.model.Jenkins;

/**
 * Copyright (c) 2018-2019, Rogue Wave Software, Inc., http://www.roguewave.com
 * <p>
 * This software is released under the terms of the
 * MIT license. See https://opensource.org/licenses/MIT
 * for more information.
 * <p>
 * Plugin version number from Jenkins
 */
class PluginVersion {

  private static String version = null;

  static synchronized String get() {
    if (version == null) {
      Plugin qrebelPlugin = Jenkins.get().getPlugin("qrebel");
      if (qrebelPlugin != null) {
        version = qrebelPlugin.getWrapper().getVersion();
      }
    }
    return version;
  }
}