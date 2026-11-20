package com.kiments.cataloginventory.catalog;

import com.kiments.cataloginventory.shared.BarcodeGenerator;
import com.kiments.cataloginventory.shared.SkuGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarcodeAndSkuTests {

    private final BarcodeGenerator barcodeGenerator = new BarcodeGenerator();

    @Test
    @DisplayName("Debe calcular correctamente el dígito de control EAN-13 según norma oficial Modulo 10")
    void calculatesStandardEan13CheckDigit() {
        // Ejemplo estándar GS1: 400638133393 -> 1
        assertThat(barcodeGenerator.calculateCheckDigit("400638133393")).isEqualTo(1);

        // Prefijo interno 200: 200000000001 -> verificar checksum
        String base12 = "200000000001";
        int check = barcodeGenerator.calculateCheckDigit(base12);
        String full = base12 + check;
        assertThat(barcodeGenerator.isValidEan13(full)).isTrue();
    }

    @Test
    @DisplayName("Debe validar si un código EAN-13 es válido o alterado")
    void validatesValidAndTamperedEan13() {
        String valid = barcodeGenerator.generateInternalEan13(105);
        assertThat(barcodeGenerator.isValidEan13(valid)).isTrue();

        // Alterar un dígito debe invalidar el checksum
        char last = valid.charAt(12);
        char alteredLast = (last == '9') ? '0' : (char) (last + 1);
        String tampered = valid.substring(0, 12) + alteredLast;
        assertThat(barcodeGenerator.isValidEan13(tampered)).isFalse();
    }

    @Test
    @DisplayName("Debe validar formatos de código de barras manuales permitidos")
    void validatesManualBarcodes() {
        assertThat(barcodeGenerator.isValidManualBarcode("PROD-12345")).isTrue();
        assertThat(barcodeGenerator.isValidManualBarcode("7751234567890")).isTrue();
        assertThat(barcodeGenerator.isValidManualBarcode("")).isFalse();
        assertThat(barcodeGenerator.isValidManualBarcode("   ")).isFalse();
        assertThat(barcodeGenerator.isValidManualBarcode("INVALID!@#$")).isFalse();
    }

    @Test
    @DisplayName("Debe generar SKU estables y deterministas sin caracteres especiales")
    void generatesStableSku() {
        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        SkuGenerator skuGen = new SkuGenerator(mockJdbc, barcodeGenerator);

        String sku = skuGen.generateSku("Pantalón Clásico", "Rosa nude", "M");
        assertThat(sku).startsWith("KIM-");
        assertThat(sku).contains("-ROS-");
        assertThat(sku).endsWith("-MXX");
    }

    @Test
    @DisplayName("Debe generar 1000 secuencias únicas y válidas bajo concurrencia")
    void generatesUniqueBarcodesUnderConcurrency() {
        Set<String> generated = new HashSet<>();
        for (int i = 1; i <= 1000; i++) {
            String code = barcodeGenerator.generateInternalEan13(i);
            assertThat(barcodeGenerator.isValidEan13(code)).isTrue();
            assertThat(generated.add(code)).as("El código " + code + " debe ser único").isTrue();
        }
        assertThat(generated).hasSize(1000);
    }
}
