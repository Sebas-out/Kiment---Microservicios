package com.kiments.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuthApplication {
  public static void main(String[] args) {
    var context = new SpringApplication(AuthApplication.class).run(args);
    boolean bootstrap =
        context.getEnvironment().getProperty("app.bootstrap.admin.enabled", Boolean.class, false)
            || context
                .getEnvironment()
                .getProperty("app.bootstrap.admin.manual-enabled", Boolean.class, false);
    if (bootstrap && !(context instanceof WebServerApplicationContext)) context.close();
  }
}


