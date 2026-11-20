package com.kiments.auth.storage;

import com.kiments.auth.exception.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileStorageService {
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
  private static final Set<String> ALLOWED_MIME_TYPES =
      Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

  private final StorageProperties properties;
  private final Path rootLocation;

  public ProfileStorageService(StorageProperties properties) {
    this.properties = properties;
    this.rootLocation = Path.of(properties.getPath()).toAbsolutePath().normalize();
    initDirectory();
  }

  public String store(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo de imagen no puede estar vacío");
    }

    if (file.getSize() > properties.getMaxSizeBytes()) {
      throw new ApiException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "El archivo excede el tamaño máximo permitido de "
              + (properties.getMaxSizeBytes() / (1024 * 1024))
              + " MB");
    }

    String originalFilename = file.getOriginalFilename();
    String extension = extractExtension(originalFilename);

    if ("webp".equals(extension)
        || "image/webp".equalsIgnoreCase(file.getContentType())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "El formato WebP no está disponible en este entorno; utilice formato JPEG o PNG");
    }

    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Formato de archivo no admitido. Solo se admiten imágenes JPEG (.jpg, .jpeg) y PNG (.png)");
    }

    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Tipo MIME no admitido (" + contentType + "). Solo se admiten image/jpeg e image/png");
    }

    BufferedImage originalImage;
    try (InputStream in = file.getInputStream()) {
      originalImage = ImageIO.read(in);
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "No se pudo leer el archivo de imagen suministrado");
    }

    if (originalImage == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "El contenido del archivo no es una imagen válida o está dañado");
    }

    int width = originalImage.getWidth();
    int height = originalImage.getHeight();
    if (width <= 0
        || height <= 0
        || width > properties.getMaxDimension()
        || height > properties.getMaxDimension()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Las dimensiones de la imagen ("
              + width
              + "x"
              + height
              + " px) exceden el límite máximo de "
              + properties.getMaxDimension()
              + "x"
              + properties.getMaxDimension()
              + " px");
    }

    String normalizedExt = "jpeg".equals(extension) ? "jpg" : extension;
    String newKey = UUID.randomUUID().toString() + "." + normalizedExt;
    Path targetPath = resolvePath(newKey);
    Path tempFile = resolvePath(".tmp_" + UUID.randomUUID().toString() + "." + normalizedExt);

    try {
      initDirectory();
      if ("jpg".equals(normalizedExt)) {
        BufferedImage rgbImage =
            new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        try {
          g.setColor(Color.WHITE);
          g.fillRect(0, 0, width, height);
          g.drawImage(originalImage, 0, 0, null);
        } finally {
          g.dispose();
        }
        ImageIO.write(rgbImage, "jpg", tempFile.toFile());
      } else {
        ImageIO.write(originalImage, "png", tempFile.toFile());
      }

      Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      return newKey;
    } catch (IOException e) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
      }
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo almacenar la fotografía de perfil en disco");
    }
  }

  public Resource load(String imageKey) {
    validateKeyFormat(imageKey);
    Path filePath = resolvePath(imageKey);
    if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
      throw ApiException.notFound();
    }
    try {
      Resource resource = new UrlResource(filePath.toUri());
      if (resource.exists() && resource.isReadable()) {
        return resource;
      }
      throw ApiException.notFound();
    } catch (MalformedURLException e) {
      throw ApiException.notFound();
    }
  }

  public boolean delete(String imageKey) {
    if (imageKey == null || imageKey.isBlank()) {
      return false;
    }
    try {
      validateKeyFormat(imageKey);
      Path filePath = resolvePath(imageKey);
      return Files.deleteIfExists(filePath);
    } catch (Exception e) {
      return false;
    }
  }

  public MediaType resolveMediaType(String imageKey) {
    if (imageKey != null && imageKey.toLowerCase(Locale.ROOT).endsWith(".png")) {
      return MediaType.IMAGE_PNG;
    }
    return MediaType.IMAGE_JPEG;
  }

  public Path getRootLocation() {
    return rootLocation;
  }

  private void initDirectory() {
    try {
      if (!Files.exists(rootLocation)) {
        Files.createDirectories(rootLocation);
      }
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "No se pudo inicializar el directorio de almacenamiento de perfiles");
    }
  }

  private Path resolvePath(String filename) {
    if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre de archivo no válido");
    }
    Path target = rootLocation.resolve(filename).normalize();
    if (!target.startsWith(rootLocation)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Acceso a ruta de almacenamiento no autorizado");
    }
    return target;
  }

  private void validateKeyFormat(String key) {
    if (key == null || !key.matches("^[a-f0-9-]{36}\\.(jpg|jpeg|png)$")) {
      throw ApiException.notFound();
    }
  }

  private String extractExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT).trim();
  }
}
