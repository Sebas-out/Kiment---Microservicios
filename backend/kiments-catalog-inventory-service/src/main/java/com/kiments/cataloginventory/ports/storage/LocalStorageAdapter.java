package com.kiments.cataloginventory.ports.storage;

import com.kiments.cataloginventory.config.StorageProperties;
import com.kiments.cataloginventory.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageAdapter.class);

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_DIMENSION = 4096;

    private final StorageProperties properties;
    private final Path rootPath;

    public LocalStorageAdapter(StorageProperties properties) {
        this.properties = properties;
        this.rootPath = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            log.error("No se pudo inicializar el directorio de almacenamiento local en {}: {}", rootPath, e.getMessage());
            throw new IllegalStateException("Error al inicializar directorio de almacenamiento local", e);
        }
    }

    @Override
    public StoredImage storeImage(byte[] bytes, String declaredMimeType, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "El archivo de imagen está vacío");
        }

        if (bytes.length > properties.getMaxFileSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "El archivo excede el tamaño máximo permitido de " + (properties.getMaxFileSizeBytes() / (1024 * 1024)) + " MB");
        }

        String mime = declaredMimeType != null ? declaredMimeType.toLowerCase().trim() : "";
        if (!ALLOWED_MIME_TYPES.contains(mime)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT",
                    "Formato de imagen no soportado. Se aceptan únicamente JPG, PNG y WEBP");
        }

        // Validación estricta decodificando la imagen para prevenir ataques políglotas o archivos falsificados
        BufferedImage originalImage;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            originalImage = ImageIO.read(bais);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORRUPT_IMAGE", "No se pudo decodificar el archivo como imagen válida");
        }

        if (originalImage == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_BYTES", "El contenido del archivo no corresponde a una imagen válida");
        }

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_EXCEEDED",
                    "Las dimensiones de la imagen exceden el límite máximo de " + MAX_DIMENSION + "x" + MAX_DIMENSION + " píxeles");
        }

        // Sanitización y re-codificación: elimina metadatos EXIF / scripts incrustados
        String formatName = mime.equals("image/png") ? "png" : "jpeg";
        String extension = mime.equals("image/png") ? ".png" : (mime.equals("image/webp") ? ".webp" : ".jpg");
        String finalMime = mime.equals("image/png") ? "image/png" : "image/jpeg";

        byte[] sanitizedBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage cleanImage;
            if (formatName.equals("png")) {
                cleanImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            } else {
                cleanImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
            Graphics2D g2d = cleanImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();

            boolean ok = ImageIO.write(cleanImage, formatName, baos);
            if (!ok) {
                // Fallback a los bytes originales si el escritor no está disponible
                sanitizedBytes = bytes;
            } else {
                sanitizedBytes = baos.toByteArray();
            }
        } catch (Exception e) {
            log.warn("Fallo al re-codificar imagen para limpieza de metadatos, usando bytes verificados: {}", e.getMessage());
            sanitizedBytes = bytes;
        }

        // Generar nombre seguro basado en UUID (evita cualquier path traversal)
        String storageKey = UUID.randomUUID().toString() + extension;
        Path targetPath = resolveSafePath(storageKey);

        try {
            Path tempPath = Files.createTempFile(rootPath, "kiments-upload-", ".tmp");
            Files.write(tempPath, sanitizedBytes);
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Error al persistir archivo en disco: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Error al guardar el archivo en el almacenamiento");
        }

        return new StoredImage(storageKey, finalMime, sanitizedBytes.length, width, height);
    }

    @Override
    public byte[] retrieveImage(String storageKey) {
        Path filePath = resolveSafePath(storageKey);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "La fotografía solicitada no existe o fue eliminada");
        }

        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Error al leer archivo {}: {}", filePath, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_READ_ERROR", "Error al leer la fotografía");
        }
    }

    @Override
    public void removeImage(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Path filePath = resolveSafePath(storageKey);
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("No se pudo eliminar el archivo físico {} (podrá eliminarse en limpieza diferida): {}", storageKey, e.getMessage());
        }
    }

    private Path resolveSafePath(String storageKey) {
        if (storageKey == null || storageKey.contains("..") || storageKey.contains("/") || storageKey.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "Identificador de almacenamiento inválido");
        }
        Path resolved = rootPath.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_DETECTED", "Ruta de almacenamiento no permitida");
        }
        return resolved;
    }
}
