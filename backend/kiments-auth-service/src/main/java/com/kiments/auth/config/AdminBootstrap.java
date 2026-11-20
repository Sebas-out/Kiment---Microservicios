package com.kiments.auth.config;

import com.kiments.auth.config.bootstrap.BootstrapAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Único punto de entrada al bootstrap. Sin endpoints HTTP ni credenciales predeterminadas. */
@Component
public class AdminBootstrap implements ApplicationRunner {
  private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);
  private final BootstrapAdminService service;
  private final Environment env;

  public AdminBootstrap(BootstrapAdminService service, Environment env) {
    this.service = service;
    this.env = env;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (env.getProperty("app.bootstrap.enabled", Boolean.class, false))
      throw new IllegalStateException(
          "La opción bootstrap antigua ya no está habilitada; utilice APP_BOOTSTRAP_ADMIN_ENABLED"
              + " en dev o el procedimiento manual explícito");
    boolean automatic = env.getProperty("app.bootstrap.admin.enabled", Boolean.class, false);
    boolean manual = env.getProperty("app.bootstrap.admin.manual-enabled", Boolean.class, false);
    if (automatic && manual)
      throw new IllegalStateException("Seleccione un único modo de inicialización");
    if (automatic || manual) {
      var result =
          service.initialize(
              automatic
                  ? BootstrapAdminService.Mode.AUTOMATIC_DEV
                  : BootstrapAdminService.Mode.MANUAL);
      LOG.info("Resultado de inicialización administrativa: {}", result);
    }
  }
}
