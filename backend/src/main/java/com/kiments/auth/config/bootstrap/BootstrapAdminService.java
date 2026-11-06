package com.kiments.auth.config.bootstrap;

public interface BootstrapAdminService {
  enum Mode {
    AUTOMATIC_DEV,
    MANUAL
  }

  enum Outcome {
    CREATED,
    DISABLED,
    ALREADY_RECORDED,
    EXISTING_DATA
  }

  Outcome initialize(Mode mode);
}
