package com.kiments.auth.branches;

import org.springframework.stereotype.Component;

@Component
public class BranchMapper {
  public BranchDtos.Response toResponse(Branch b) {
    return new BranchDtos.Response(
        b.getId(),
        b.getCode(),
        b.getName(),
        b.getAddress(),
        b.getPhone(),
        b.getCity(),
        b.getUbigeo(),
        b.getEmail(),
        b.getBranchType(),
        b.getSunatCode(),
        b.isEnabled(),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }
}
