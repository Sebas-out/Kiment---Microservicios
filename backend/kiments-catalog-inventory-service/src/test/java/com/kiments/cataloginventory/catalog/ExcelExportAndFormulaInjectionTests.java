package com.kiments.cataloginventory.catalog;

import com.kiments.cataloginventory.shared.ExcelExportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportAndFormulaInjectionTests {

    private final ExcelExportService exportService = new ExcelExportService();

    @Test
    @DisplayName("Debe sanitizar intentos de inyección de fórmulas (CSV/Excel Formula Injection)")
    void sanitizesFormulaInjectionAttempts() {
        assertThat(ExcelExportService.sanitizeFormula("=cmd|'/C calc'!A0")).isEqualTo("'=cmd|'/C calc'!A0");
        assertThat(ExcelExportService.sanitizeFormula("+1+2")).isEqualTo("'+1+2");
        assertThat(ExcelExportService.sanitizeFormula("-2+5")).isEqualTo("'-2+5");
        assertThat(ExcelExportService.sanitizeFormula("@SUM(A1:A10)")).isEqualTo("'@SUM(A1:A10)");
        assertThat(ExcelExportService.sanitizeFormula("Texto Seguro")).isEqualTo("Texto Seguro");
        assertThat(ExcelExportService.sanitizeFormula("")).isEqualTo("");
        assertThat(ExcelExportService.sanitizeFormula(null)).isEqualTo("");
    }

    @Test
    @DisplayName("Debe generar archivo XLSX válido y con celdas de texto protegidas")
    void generatesValidXlsxWorkbook() throws IOException {
        List<String> headers = List.of("Producto", "SKU", "Precio", "Fórmula Sospechosa");
        List<List<Object>> rows = List.of(
                List.of("Pantalón Jean", "KIM-100-AZU-M", 89.90, "=SUM(1,2)"),
                List.of("Camisa Lino", "KIM-101-BLA-L", 120.00, "+HYPERLINK(\"http://evil.com\")")
        );

        byte[] xlsxBytes = exportService.exportToExcel("Inventario", headers, rows);
        assertThat(xlsxBytes).isNotEmpty();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheet("Inventario");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);

            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Producto");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Fórmula Sospechosa");

            Row dataRow1 = sheet.getRow(1);
            assertThat(dataRow1.getCell(0).getStringCellValue()).isEqualTo("Pantalón Jean");
            // La celda sospechosa debe comenzar con comilla simple
            assertThat(dataRow1.getCell(3).getStringCellValue()).startsWith("'=");

            Row dataRow2 = sheet.getRow(2);
            assertThat(dataRow2.getCell(3).getStringCellValue()).startsWith("'+");
        }
    }
}
