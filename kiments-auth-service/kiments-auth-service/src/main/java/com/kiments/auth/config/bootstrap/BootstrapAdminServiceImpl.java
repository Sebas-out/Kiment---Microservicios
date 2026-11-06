package com.kiments.auth.config.bootstrap;

import com.kiments.auth.roles.RoleRepository;
import com.kiments.auth.shared.SecurityMutationLock;
import com.kiments.auth.users.*;
import jakarta.validation.Validator;
import org.flywaydb.core.Flyway;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdminServiceImpl implements BootstrapAdminService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final InitializationRepository initialization;
  private final SecurityMutationLock lock;
  private final PasswordEncoder passwords;
  private final Validator validator;
  private final Environment env;
  private final ApplicationContext context;
  private final Flyway flyway;

  public BootstrapAdminServiceImpl(
      UserRepository users,
      RoleRepository roles,
      InitializationRepository initialization,
      SecurityMutationLock lock,
      PasswordEncoder passwords,
      Validator validator,
      Environment env,
      ApplicationContext context,
      Flyway flyway) {
    this.users = users;
    this.roles = roles;
    this.initialization = initialization;
    this.lock = lock;
    this.passwords = passwords;
    this.validator = validator;
    this.env = env;
    this.context = context;
    this.flyway = flyway;
  }

  @Override
  @Transactional
  public Outcome initialize(Mode mode) {
    String flag =
        mode == Mode.AUTOMATIC_DEV
            ? "app.bootstrap.admin.enabled"
            : "app.bootstrap.admin.manual-enabled";
    if (!env.getProperty(flag, Boolean.class, false)) return Outcome.DISABLED;
    if (mode == Mode.AUTOMATIC_DEV
        && !env.getProperty("APP_BOOTSTRAP_ADMIN_ENABLED", Boolean.class, false))
      return Outcome.DISABLED;
    if (mode == Mode.AUTOMATIC_DEV && !env.acceptsProfiles(Profiles.of("dev & !prod")))
      throw new IllegalStateException(
          "Inicialización automática permitida solo en dev, sin perfil prod");
    if (mode == Mode.MANUAL && context instanceof WebServerApplicationContext)
      throw new IllegalStateException("Inicialización manual requiere ejecución sin servidor HTTP");
    // ApplicationRunner se invoca después de Flyway/JPA. Fallar antes de cualquier escritura si hay
    // inconsistencias.
    if (!flyway.validateWithResult().validationSuccessful || flyway.info().pending().length != 0)
      throw new IllegalStateException(
          "Flyway debe terminar y validar todas las migraciones antes del bootstrap");
    lock.acquire();
    if (!initialization.isPending()) return Outcome.ALREADY_RECORDED;
    if (users.count() != 0 || initialization.hasSessions()) {
      initialization.blockExistingData();
      return Outcome.EXISTING_DATA;
    }
    var request =
        new UserDtos.Create(
            required("APP_BOOTSTRAP_ADMIN_FIRST_NAME"),
            required("APP_BOOTSTRAP_ADMIN_LAST_NAME"),
            required("APP_BOOTSTRAP_ADMIN_DNI"),
            null,
            required("APP_BOOTSTRAP_ADMIN_EMAIL"),
            required("APP_BOOTSTRAP_ADMIN_PASSWORD"),
            "ADMIN",
            true);
    if (!validator.validate(request).isEmpty())
      throw new IllegalStateException(
          "Datos bootstrap inválidos: nombres obligatorios, DNI de 8 dígitos, correo válido y"
              + " contraseña de 12 a 128 caracteres");
    var admin =
        roles
            .findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("El catálogo ADMIN no está preparado"));
    var requiredPermissions =
        java.util.Set.of(
            "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DISABLE", "USER_ROLE_CHANGE");
    if (!admin.getPermissions().stream()
        .map(p -> p.getCode())
        .collect(java.util.stream.Collectors.toSet())
        .containsAll(requiredPermissions))
      throw new IllegalStateException("El catálogo ADMIN no contiene los permisos necesarios");
    var user = new User();
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    user.setDni(request.dni());
    user.setEmail(request.email());
    user.setPasswordHash(passwords.encode(request.password()));
    user.setRole(admin);
    user.setEnabled(true);
    users.saveAndFlush(user);
    initialization.complete(user.getId(), mode.name());
    return Outcome.CREATED;
  }

  private String required(String name) {
    String value = env.getProperty(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Falta una variable obligatoria del bootstrap: " + name);
    return value;
  }
}
