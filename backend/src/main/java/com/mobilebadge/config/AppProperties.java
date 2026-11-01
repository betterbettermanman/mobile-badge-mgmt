package com.mobilebadge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private final Storage storage = new Storage();
  private final Seed seed = new Seed();

  public Storage getStorage() {
    return storage;
  }

  public Seed getSeed() {
    return seed;
  }

  public static class Storage {
    private String root = "./data/objects";

    public String getRoot() {
      return root;
    }

    public void setRoot(String root) {
      this.root = root;
    }
  }

  public static class Seed {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
