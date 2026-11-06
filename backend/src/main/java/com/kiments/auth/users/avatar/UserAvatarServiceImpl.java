package com.kiments.auth.users.avatar;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.security.CurrentUser;
import com.kiments.auth.shared.SecurityMutationLock;
import com.kiments.auth.storage.ProfileStorageService;
import com.kiments.auth.users.User;
import com.kiments.auth.users.UserDtos;
import com.kiments.auth.users.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserAvatarServiceImpl implements UserAvatarService {
  private static final Logger log = LoggerFactory.getLogger(UserAvatarServiceImpl.class);

  private final UserRepository users;
  private final ProfileStorageService storage;
  private final CurrentUser current;
  private final SecurityMutationLock lock;

  public UserAvatarServiceImpl(
      UserRepository users,
      ProfileStorageService storage,
      CurrentUser current,
      SecurityMutationLock lock) {
    this.users = users;
    this.storage = storage;
    this.current = current;
    this.lock = lock;
  }

  @Override
  @Transactional
  public UserDtos.AvatarResponse uploadAvatar(MultipartFile file) {
    lock.acquire();
    User user = current.require();

    // 1. Validar y almacenar en disco el nuevo archivo de forma segura
    String newKey = storage.store(file);
    String oldKey = user.getProfileImageKey();

    // 2. Registrar sincronizaciones transaccionales:
    // - afterCommit: eliminar archivo anterior solo tras confirmación definitiva en BD
    // - afterCompletion: limpiar archivo nuevo si ocurre rollback
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
                try {
                  storage.delete(oldKey);
                } catch (Exception ex) {
                  log.warn("Error al eliminar fotografía anterior tras commit: {}", oldKey, ex);
                }
              }
            }

            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) {
                try {
                  storage.delete(newKey);
                } catch (Exception ex) {
                  log.warn("Error al limpiar archivo nuevo tras rollback: {}", newKey, ex);
                }
              }
            }
          });
    }

    // 3. Persistir referencia en base de datos.
    try {
      user.setProfileImageKey(newKey);
      users.saveAndFlush(user);
    } catch (RuntimeException ex) {
      if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        try {
          storage.delete(newKey);
        } catch (Exception ignored) {
        }
      }
      throw ex;
    }

    // Si no hay gestor de transacciones activo (ej. pruebas unitarias simples), ejecutar eliminación directa
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
        try {
          storage.delete(oldKey);
        } catch (Exception ignored) {
        }
      }
    }

    return new UserDtos.AvatarResponse("Fotografía de perfil actualizada con éxito", true);
  }

  @Override
  @Transactional(readOnly = true)
  public AvatarDownload getAvatar() {
    User user = current.require();
    return getUserAvatar(user.getId());
  }

  @Override
  @Transactional
  public void deleteAvatar() {
    User user = current.require();
    deleteUserAvatar(user.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public AvatarDownload getUserAvatar(UUID targetUserId) {
    User actor = current.require();
    boolean isSelf = actor.getId().equals(targetUserId);
    if (!isSelf) {
      boolean isAdminWithRead =
          actor.getRole() != null
              && "ADMIN".equals(actor.getRole().getCode())
              && actor.getRole().getPermissions() != null
              && actor.getRole().getPermissions().stream()
                  .anyMatch(p -> "USER_READ".equals(p.getCode()));

      if (!isAdminWithRead) {
        throw ApiException.forbidden();
      }
    }

    User target =
        isSelf ? actor : users.findById(targetUserId).orElseThrow(ApiException::notFound);
    String key = target.getProfileImageKey();
    if (key == null || key.isBlank()) {
      throw ApiException.notFound();
    }
    return new AvatarDownload(storage.load(key), storage.resolveMediaType(key));
  }

  @Override
  @Transactional
  public void deleteUserAvatar(UUID targetUserId) {
    lock.acquire();
    User actor = current.require();
    boolean isSelf = actor.getId().equals(targetUserId);
    if (!isSelf) {
      boolean isAdminWithUpdate =
          actor.getRole() != null
              && "ADMIN".equals(actor.getRole().getCode())
              && actor.getRole().getPermissions() != null
              && actor.getRole().getPermissions().stream()
                  .anyMatch(p -> "USER_UPDATE".equals(p.getCode()));

      if (!isAdminWithUpdate) {
        throw ApiException.forbidden();
      }
    }

    User target =
        isSelf ? actor : users.findById(targetUserId).orElseThrow(ApiException::notFound);
    String oldKey = target.getProfileImageKey();
    if (oldKey == null || oldKey.isBlank()) {
      return;
    }

    target.setProfileImageKey(null);
    users.saveAndFlush(target);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                storage.delete(oldKey);
              } catch (Exception ex) {
                log.warn("Error al eliminar fotografía de usuario tras commit: {}", oldKey, ex);
              }
            }
          });
    } else {
      try {
        storage.delete(oldKey);
      } catch (Exception ignored) {
      }
    }
  }
}
