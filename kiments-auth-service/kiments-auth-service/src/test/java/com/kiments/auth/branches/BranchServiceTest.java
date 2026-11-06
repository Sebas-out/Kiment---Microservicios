package com.kiments.auth.branches;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.security.CurrentUser;
import com.kiments.auth.shared.SecurityMutationLock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

  @Mock BranchRepository repository;
  @Mock BranchMapper mapper;
  @Mock CurrentUser current;
  @Mock SecurityMutationLock lock;

  BranchService service;

  @BeforeEach
  void setup() {
    service = new BranchServiceImpl(repository, mapper, current, lock);
  }

  // ── Utilitarios ──────────────────────────────────────────────────────────

  void conflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable op) {
    assertThatThrownBy(op)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
  }

  void notFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable op) {
    assertThatThrownBy(op)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  BranchDtos.Create validCreate() {
    return new BranchDtos.Create(
        "SUR01", "Sucursal Sur", "Av. Principal 100", null, BranchType.VENTA, true);
  }

  // ── Creación ─────────────────────────────────────────────────────────────

  @Test
  void duplicateCodeIsRejectedBeforeSaving() {
    when(repository.existsByCodeIgnoreCase("SUR01")).thenReturn(true);
    conflict(() -> service.create(validCreate()));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void missingPermissionBlocksCreateBeforeRepository() {
    when(current.requireAdmin("BRANCH_CREATE")).thenThrow(ApiException.forbidden());
    assertThatThrownBy(() -> service.create(validCreate()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    var order = inOrder(lock, current);
    order.verify(lock).acquire();
    order.verify(current).requireAdmin("BRANCH_CREATE");
    verifyNoInteractions(repository);
  }

  @Test
  void createNormalizesCodeToUppercase() {
    var req =
        new BranchDtos.Create("sur01", "Sucursal Sur", null, null, BranchType.VENTA, true);
    when(repository.existsByCodeIgnoreCase("SUR01")).thenReturn(false);
    var captured = ArgumentCaptor.forClass(Branch.class);
    when(repository.saveAndFlush(captured.capture())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(null);
    service.create(req);
    assertThat(captured.getValue().getCode()).isEqualTo("SUR01");
    assertThat(captured.getValue().getBranchType()).isEqualTo(BranchType.VENTA);
  }

  @Test
  void createWithAllSunatAndOperationalDetails() {
    var req =
        new BranchDtos.Create(
            "ALM01",
            "Almacén Central",
            "Av. Los Ficus 500",
            "+51 1 2345678",
            "Lima",
            "150101",
            "almacen@kiments.com",
            BranchType.ALMACEN,
            "0001",
            true);
    when(repository.existsByCodeIgnoreCase("ALM01")).thenReturn(false);
    when(repository.existsBySunatCode("0001")).thenReturn(false);
    var captured = ArgumentCaptor.forClass(Branch.class);
    when(repository.saveAndFlush(captured.capture())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(null);

    service.create(req);

    Branch b = captured.getValue();
    assertThat(b.getCode()).isEqualTo("ALM01");
    assertThat(b.getBranchType()).isEqualTo(BranchType.ALMACEN);
    assertThat(b.getCity()).isEqualTo("Lima");
    assertThat(b.getUbigeo()).isEqualTo("150101");
    assertThat(b.getEmail()).isEqualTo("almacen@kiments.com");
    assertThat(b.getSunatCode()).isEqualTo("0001");
  }

  @Test
  void duplicateSunatCodeIsRejectedOnCreate() {
    var req =
        new BranchDtos.Create(
            "ALM02",
            "Almacén Dos",
            null,
            null,
            "Lima",
            "150101",
            null,
            BranchType.ALMACEN,
            "0001",
            true);
    when(repository.existsByCodeIgnoreCase("ALM02")).thenReturn(false);
    when(repository.existsBySunatCode("0001")).thenReturn(true);

    conflict(() -> service.create(req));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void createNormalizesEmailToLowercase() {
    var req =
        new BranchDtos.Create(
            "TIENDA1",
            "Tienda Uno",
            null,
            null,
            null,
            null,
            "  ADMIN.TIENDA@KIMENTS.COM  ",
            BranchType.MIXTO,
            null,
            true);
    when(repository.existsByCodeIgnoreCase("TIENDA1")).thenReturn(false);
    var captured = ArgumentCaptor.forClass(Branch.class);
    when(repository.saveAndFlush(captured.capture())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(null);

    service.create(req);
    assertThat(captured.getValue().getEmail()).isEqualTo("admin.tienda@kiments.com");
    assertThat(captured.getValue().getBranchType()).isEqualTo(BranchType.MIXTO);
  }

  // ── Consulta ─────────────────────────────────────────────────────────────

  @Test
  void getUnknownBranchReturnsNotFound() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());
    notFound(() -> service.get(id));
  }

  @Test
  void missingPermissionBlocksGet() {
    when(current.requireAdmin("BRANCH_READ")).thenThrow(ApiException.forbidden());
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(repository);
  }

  // ── Edición (PATCH) ──────────────────────────────────────────────────────

  @Test
  void updateUnknownBranchReturnsNotFound() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());
    notFound(
        () ->
            service.update(
                id,
                new BranchDtos.Update(
                    "Nuevo nombre", null, null, null, null, null, null, null)));
  }

  @Test
  void missingPermissionBlocksUpdate() {
    when(current.requireAdmin("BRANCH_UPDATE")).thenThrow(ApiException.forbidden());
    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(),
                    new BranchDtos.Update("X", null, null, null, null, null, null, null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    var order = inOrder(lock, current);
    order.verify(lock).acquire();
    order.verify(current).requireAdmin("BRANCH_UPDATE");
    verifyNoInteractions(repository);
  }

  @Test
  void patchDifferentiatesBetweenConservingAndCleaningOptionalFields() {
    UUID id = UUID.randomUUID();
    var branch = new Branch("SUC01");
    branch.setName("Nombre Original");
    branch.setAddress("Dirección Original");
    branch.setCity("Lima");
    branch.setUbigeo("150101");
    branch.setEmail("contacto@kiments.com");
    branch.setBranchType(BranchType.VENTA);
    branch.setSunatCode("0001");

    when(repository.findById(id)).thenReturn(Optional.of(branch));
    when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(null);

    // Caso 1: Actualizar name y tipo; limpiar city y ubigeo con cadenas vacías; conservar email y sunatCode omitiéndolos (null)
    var updateReq =
        new BranchDtos.Update(
            "Nombre Modificado",
            null, // address null -> se conserva "Dirección Original"
            null, // phone null -> se conserva
            "   ", // city en blanco -> se limpia a null
            "", // ubigeo vacío -> se limpia a null
            null, // email null -> se conserva "contacto@kiments.com"
            BranchType.MIXTO, // branchType no null -> se actualiza a MIXTO
            null // sunatCode null -> se conserva "0001"
            );

    service.update(id, updateReq);

    assertThat(branch.getName()).isEqualTo("Nombre Modificado");
    assertThat(branch.getAddress()).isEqualTo("Dirección Original");
    assertThat(branch.getCity()).isNull();
    assertThat(branch.getUbigeo()).isNull();
    assertThat(branch.getEmail()).isEqualTo("contacto@kiments.com");
    assertThat(branch.getBranchType()).isEqualTo(BranchType.MIXTO);
    assertThat(branch.getSunatCode()).isEqualTo("0001");
  }

  @Test
  void updateDuplicateSunatCodeIsRejected() {
    UUID id = UUID.randomUUID();
    var branch = new Branch("SUC01");
    when(repository.findById(id)).thenReturn(Optional.of(branch));
    when(repository.existsBySunatCodeAndIdNot("0002", id)).thenReturn(true);

    var updateReq =
        new BranchDtos.Update(null, null, null, null, null, null, null, "0002");

    conflict(() -> service.update(id, updateReq));
    verify(repository, never()).saveAndFlush(any());
  }

  // ── Desactivación ────────────────────────────────────────────────────────

  @Test
  void deactivationIsBlockedWhenActiveWorkersExist() {
    UUID id = UUID.randomUUID();
    var branch = new Branch("CENTRAL");
    when(repository.findById(id)).thenReturn(Optional.of(branch));
    when(repository.countActiveWorkers(id)).thenReturn(3L);
    conflict(() -> service.changeStatus(id, false));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void deactivationProceedsWhenNoActiveWorkersExist() {
    UUID id = UUID.randomUUID();
    var branch = new Branch("VACIA");
    branch.setEnabled(true);
    when(repository.findById(id)).thenReturn(Optional.of(branch));
    when(repository.countActiveWorkers(id)).thenReturn(0L);
    when(repository.saveAndFlush(branch)).thenReturn(branch);
    when(mapper.toResponse(branch)).thenReturn(null);
    service.changeStatus(id, false);
    assertThat(branch.isEnabled()).isFalse();
    verify(repository).saveAndFlush(branch);
  }

  @Test
  void activationDoesNotCheckActiveWorkers() {
    UUID id = UUID.randomUUID();
    var branch = new Branch("INACTIVA");
    branch.setEnabled(false);
    when(repository.findById(id)).thenReturn(Optional.of(branch));
    when(repository.saveAndFlush(branch)).thenReturn(branch);
    when(mapper.toResponse(branch)).thenReturn(null);
    service.changeStatus(id, true);
    assertThat(branch.isEnabled()).isTrue();
    verify(repository, never()).countActiveWorkers(any());
  }

  @Test
  void deactivationErrorMessageIncludesWorkerCount() {
    UUID id = UUID.randomUUID();
    var branch = new Branch("TEST");
    when(repository.findById(id)).thenReturn(Optional.of(branch));
    when(repository.countActiveWorkers(id)).thenReturn(1L);
    assertThatThrownBy(() -> service.changeStatus(id, false))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getMessage()).contains("1").contains("activo asignado");
            });
  }
}
