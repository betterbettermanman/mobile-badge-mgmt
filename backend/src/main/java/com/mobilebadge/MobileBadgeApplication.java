package com.mobilebadge;

import com.mobilebadge.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MobileBadgeApplication {

  public static void main(String[] args) {
    SpringApplication.run(MobileBadgeApplication.class, args);
  }
}
