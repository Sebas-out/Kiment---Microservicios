package com.kiments.auth.authentication;

public interface AuthenticationService {
  AuthenticationDtos.TokenResponse login(AuthenticationDtos.Login request);

  AuthenticationDtos.Profile me();

  void changePassword(AuthenticationDtos.ChangePassword request);

  AuthenticationDtos.TokenResponse refresh(String token);

  void logout(String token);
}
