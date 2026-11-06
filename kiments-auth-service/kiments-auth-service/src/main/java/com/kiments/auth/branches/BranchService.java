package com.kiments.auth.branches;

import java.util.UUID;

public interface BranchService {

  BranchDtos.Response create(BranchDtos.Create request);

  BranchDtos.Response get(UUID id);

  BranchDtos.PageResponse<BranchDtos.Response> list(
      String name,
      String code,
      BranchType type,
      String city,
      String sunatCode,
      Boolean enabled,
      int page,
      int size);

  BranchDtos.Response update(UUID id, BranchDtos.Update request);

  BranchDtos.Response changeStatus(UUID id, boolean enabled);
}
