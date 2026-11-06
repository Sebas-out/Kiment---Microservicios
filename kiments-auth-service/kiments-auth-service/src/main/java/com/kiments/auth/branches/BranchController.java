package com.kiments.auth.branches;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
@Validated
public class BranchController {

  private final BranchService service;

  public BranchController(BranchService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('BRANCH_CREATE')")
  public ResponseEntity<BranchDtos.Response> create(@Valid @RequestBody BranchDtos.Create request) {
    var branch = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/branches/" + branch.id())).body(branch);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('BRANCH_READ')")
  public BranchDtos.PageResponse<BranchDtos.Response> list(
      @RequestParam(required = false) @Size(max = 100) String name,
      @RequestParam(required = false)
          @Pattern(regexp = "[A-Z0-9_-]{0,20}")
          String code,
      @RequestParam(required = false) BranchType type,
      @RequestParam(required = false) @Size(max = 100) String city,
      @RequestParam(required = false)
          @Pattern(regexp = "[0-9]{0,4}")
          String sunatCode,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(name, code, type, city, sunatCode, enabled, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('BRANCH_READ')")
  public BranchDtos.Response get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('BRANCH_UPDATE')")
  public BranchDtos.Response update(
      @PathVariable UUID id, @Valid @RequestBody BranchDtos.Update request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('BRANCH_DISABLE')")
  public BranchDtos.Response status(
      @PathVariable UUID id, @Valid @RequestBody BranchDtos.ChangeStatus request) {
    return service.changeStatus(id, request.enabled());
  }
}
