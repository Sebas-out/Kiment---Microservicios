package com.kiments.cataloginventory.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SkuGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final BarcodeGenerator barcodeGenerator;
    private final AtomicLong fallbackSequence = new AtomicLong(1000);

    public SkuGenerator(JdbcTemplate jdbcTemplate, BarcodeGenerator barcodeGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.barcodeGenerator = barcodeGenerator;
    }

    public long getNextSkuSequence() {
        try {
            Long val = jdbcTemplate.queryForObject("SELECT nextval('catalog_inventory.sku_seq')", Long.class);
            return val != null ? val : fallbackSequence.incrementAndGet();
        } catch (Exception e) {
            return fallbackSequence.incrementAndGet();
        }
    }

    public long getNextBarcodeSequence() {
        try {
            Long val = jdbcTemplate.queryForObject("SELECT nextval('catalog_inventory.barcode_seq')", Long.class);
            return val != null ? val : fallbackSequence.incrementAndGet();
        } catch (Exception e) {
            return fallbackSequence.incrementAndGet();
        }
    }

    /**
     * Genera un SKU estandarizado y seguro bajo concurrencia.
     * Ejemplo: KIM-1002-ROS-M
     */
    public String generateSku(String productName, String colorName, String sizeName) {
        long seq = getNextSkuSequence();
        String c = cleanCode(colorName, 3);
        String s = cleanCode(sizeName, 3);
        return String.format("KIM-%d-%s-%s", seq, c, s);
    }

    /**
     * Genera un código de barras EAN-13 automático a partir de la secuencia segura.
     */
    public String generateBarcode() {
        long seq = getNextBarcodeSequence();
        return barcodeGenerator.generateInternalEan13(seq);
    }

    private String cleanCode(String input, int length) {
        if (input == null || input.isBlank()) {
            return "X".repeat(length);
        }
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (normalized.length() >= length) {
            return normalized.substring(0, length);
        }
        return (normalized + "X".repeat(length)).substring(0, length);
    }
}
