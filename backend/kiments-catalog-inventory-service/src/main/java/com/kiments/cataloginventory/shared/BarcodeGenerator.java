package com.kiments.cataloginventory.shared;

import org.springframework.stereotype.Component;

/**
 * Generador y validador de códigos de barras EAN-13.
 *
 * <p><strong>Nota GS1 importante:</strong> Los códigos generados con prefijo interno 200–299
 * corresponden al rango internacional reservado por GS1 para "distribución restringida" (Restricted
 * Distribution Numbers / RDN) dentro del propio establecimiento o comercio. No requieren asignación
 * oficial de la organización GS1 para su uso interno en punto de venta, pero cumplen estrictamente
 * con la especificación de 13 dígitos y el algoritmo oficial de dígito de control (Check Digit Modulo 10).</p>
 */
@Component
public class BarcodeGenerator {

    private static final String INTERNAL_PREFIX = "200";

    /**
     * Genera un código de barras EAN-13 válido a partir de un número de secuencia interno.
     *
     * @param sequenceNumber número secuencial único y concurrente (1 a 999999999)
     * @return código EAN-13 de 13 dígitos con checksum válido
     */
    public String generateInternalEan13(long sequenceNumber) {
        long boundedSeq = Math.abs(sequenceNumber) % 1_000_000_000L;
        String payload9 = String.format("%09d", boundedSeq);
        String base12 = INTERNAL_PREFIX + payload9;
        int checkDigit = calculateCheckDigit(base12);
        return base12 + checkDigit;
    }

    /**
     * Calcula el dígito verificador estándar EAN-13 (Módulo 10).
     */
    public int calculateCheckDigit(String twelveDigits) {
        if (twelveDigits == null || twelveDigits.length() != 12 || !twelveDigits.matches("\\d{12}")) {
            throw new IllegalArgumentException("Se requieren exactamente 12 dígitos numéricos para calcular el checksum EAN-13");
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(twelveDigits.charAt(i));
            // Posiciones impares (índices pares 0, 2, 4...) factor 1
            // Posiciones pares (índices impares 1, 3, 5...) factor 3
            sum += (i % 2 == 0) ? digit : digit * 3;
        }

        int mod = sum % 10;
        return (mod == 0) ? 0 : 10 - mod;
    }

    /**
     * Valida si una cadena cumple con el formato EAN-13 y su dígito verificador.
     */
    public boolean isValidEan13(String barcode) {
        if (barcode == null || barcode.length() != 13 || !barcode.matches("\\d{13}")) {
            return false;
        }
        String base12 = barcode.substring(0, 12);
        int expectedCheckDigit = calculateCheckDigit(base12);
        int actualCheckDigit = Character.getNumericValue(barcode.charAt(12));
        return expectedCheckDigit == actualCheckDigit;
    }

    /**
     * Valida formato general de código de barras manual (EAN-13, EAN-8, UPC-A, Code-128 alfanumérico).
     */
    public boolean isValidManualBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        String trimmed = barcode.trim();
        // Permite códigos estándar (8 a 60 caracteres alfanuméricos seguros)
        return trimmed.matches("^[A-Za-z0-9_-]{3,60}$");
    }
}
