package com.kiments.cataloginventory.shared;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

    public static final int MAX_EXPORT_ROWS = 5000;

    /**
     * Sanitiza el valor para prevenir ataques de inyección de fórmulas CSV/Excel (CSV Formula Injection).
     * Si la celda comienza con '=', '+', '-', '@' o tabulaciones, se antepone una comilla simple.
     */
    public static String sanitizeFormula(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        char first = trimmed.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + trimmed;
        }
        return trimmed;
    }

    public byte[] exportToExcel(String sheetName, List<String> headers, List<List<Object>> rows) {
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("El número de registros excede el límite máximo de exportación de " + MAX_EXPORT_ROWS + " filas.");
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName != null ? sheetName : "Datos");

            // Estilos para encabezados
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Fila de encabezados
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
            }

            // Filas de datos
            int rowIdx = 1;
            for (List<Object> rowData : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int col = 0; col < rowData.size(); col++) {
                    Cell cell = row.createCell(col);
                    Object val = rowData.get(col);
                    if (val == null) {
                        cell.setCellValue("");
                    } else if (val instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else if (val instanceof Boolean b) {
                        cell.setCellValue(b);
                    } else {
                        cell.setCellValue(sanitizeFormula(val.toString()));
                    }
                }
            }

            // Auto-ajuste de columnas
            for (int col = 0; col < headers.size(); col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar archivo Excel", e);
        }
    }
}
