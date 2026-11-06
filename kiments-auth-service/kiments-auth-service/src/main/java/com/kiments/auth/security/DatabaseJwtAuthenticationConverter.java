package com.kiments.auth.security;

import com.kiments.auth.branches.Branch;
import com.kiments.auth.branches.BranchRepository;
import com.kiments.auth.users.*;
import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {
  private final UserRepository users;
  private final BranchRepository branches;

  public DatabaseJwtAuthenticationConverter(UserRepository users, BranchRepository branches) {
    this.users = users;
    this.branches = branches;
  }

  @Override
  @Transactional(readOnly = true)
  public AbstractAuthenticationToken convert(Jwt jwt) {
    var user =
        users
            .findById(UUID.fromString(jwt.getSubject()))
            .filter(User::isEnabled)
            .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));

    String tokenBranchId = jwt.getClaimAsString("branch_id");

    if (user.getRole().getCode().equals("ADMIN")) {
      if (user.getBranchId() != null || tokenBranchId != null) {
        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
      }
    } else {
      if (user.getBranchId() == null) {
        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
      }
      var branch = branches.findById(user.getBranchId()).filter(Branch::isEnabled).orElse(null);
      if (branch == null) {
        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
      }
      if (tokenBranchId == null || !tokenBranchId.equals(user.getBranchId().toString())) {
        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
      }
    }

    String tokenFamilyId = jwt.getClaimAsString("family_id");
    if (user.getActiveFamilyId() == null
        || tokenFamilyId == null
        || !tokenFamilyId.equals(user.getActiveFamilyId().toString())) {
      throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }

    var authorities = new ArrayList<SimpleGrantedAuthority>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
    user.getRole()
        .getPermissions()
        .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getCode())));
    return new JwtAuthenticationToken(jwt, authorities, user.getId().toString());
  }
}
