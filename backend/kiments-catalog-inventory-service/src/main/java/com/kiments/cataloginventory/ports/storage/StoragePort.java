package com.kiments.cataloginventory.ports.storage;

public interface StoragePort {

    record StoredImage(
            String storageKey,
            String mimeType,
            long byteSize,
            int width,
            int height
    ) {}

    StoredImage storeImage(byte[] bytes, String declaredMimeType, String originalFilename);

    byte[] retrieveImage(String storageKey);

    void removeImage(String storageKey);
}
