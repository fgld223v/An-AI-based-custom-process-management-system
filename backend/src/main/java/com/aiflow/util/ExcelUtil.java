package com.aiflow.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

/**
 * Apache POI XLSX 工具类 — 创建模板、读取数据、写入流。
 */
public final class ExcelUtil {

    private ExcelUtil() {}

    /**
     * 创建包含表头和若干数据行的 XSSFWorkbook。
     */
    public static XSSFWorkbook createWorkbook(String sheetName, String[] headers, List<String[]> rows) {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(sheetName);

        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            String[] rowData = rows.get(r);
            for (int c = 0; c < rowData.length; c++) {
                row.createCell(c).setCellValue(rowData[c] != null ? rowData[c] : "");
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        return wb;
    }

    /**
     * 读取 Excel 输入流，返回表头→值的 Map 列表（第一行为表头）。
     */
    public static List<Map<String, String>> readExcel(InputStream inputStream) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        XSSFWorkbook wb = new XSSFWorkbook(inputStream);
        Sheet sheet = wb.getSheetAt(0);

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            wb.close();
            return result;
        }

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            headers.add(cell != null ? cell.getStringCellValue().trim() : "");
        }

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = row.getCell(c);
                rowMap.put(headers.get(c), getCellString(cell));
            }
            result.add(rowMap);
        }

        wb.close();
        return result;
    }

    /**
     * 将 XSSFWorkbook 写入输出流并 flush。调用方负责关闭 workbook。
     */
    public static void writeToStream(XSSFWorkbook wb, OutputStream os) throws IOException {
        wb.write(os);
        os.flush();
    }

    private static boolean isRowEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !getCellString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}
