
package com.example.XML2TML.service;

import java.io.ByteArrayOutputStream;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.example.XML2TML.service.XmlLayoutService.FieldRow;
import com.example.XML2TML.service.XmlLayoutService.Layout;
import com.example.XML2TML.service.XmlLayoutService.SheetLayout;

@Service
public class ExcelExportService {

    public byte[] createWorkbook(Layout layout) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle title = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);

            CellStyle header = wb.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);

            Set<String> usedNames = new HashSet<>();

            for (SheetLayout layoutSheet : layout.sheets()) {
                String sheetName = uniqueSheetName(
                        layoutSheet.name(), usedNames);

                Sheet sheet = wb.createSheet(sheetName);
                sheet.createFreezePane(0, 4);

                Row titleRow = sheet.createRow(0);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(
                        layout.groupId() + ": " + layout.groupName());
                titleCell.setCellStyle(title);
                sheet.addMergedRegion(
                        new CellRangeAddress(0, 0, 0, 10));

                Row branchRow = sheet.createRow(1);
                branchRow.createCell(0).setCellValue(
                        "Section: " + layoutSheet.section());
                branchRow.createCell(1).setCellValue(
                        "Branch: " + layoutSheet.branch());
                branchRow.createCell(2).setCellValue(
                        layoutSheet.condition());

                String[] columns = {
                        "From", "To", "Length", "Data Type",
                        "Description", "Value", "Node Type",
                        "Branch", "Format", "Repeats", "Record Length"
                };

                Row headings = sheet.createRow(3);
                for (int i = 0; i < columns.length; i++) {
                    Cell cell = headings.createCell(i);
                    cell.setCellValue(columns[i]);
                    cell.setCellStyle(header);
                }

                int rowIndex = 4;

                for (FieldRow field : layoutSheet.rows()) {
                    Row row = sheet.createRow(rowIndex++);

                    row.createCell(0).setCellValue(field.from());
                    row.createCell(1).setCellValue(field.to());
                    row.createCell(2).setCellValue(field.length());
                    row.createCell(3).setCellValue(field.dataType());
                    row.createCell(4).setCellValue(field.description());
                    row.createCell(5).setCellValue(field.value());
                    row.createCell(6).setCellValue(field.nodeType());
                    row.createCell(7).setCellValue(field.branch());
                    row.createCell(8).setCellValue(field.format());
                    row.createCell(9).setCellValue(field.repeats());
                    row.createCell(10).setCellValue(field.recordLength());
                }

                int[] widths = {
                        10, 10, 10, 18, 38, 24, 16, 20, 18, 12, 16
                };

                for (int i = 0; i < widths.length; i++) {
                    sheet.setColumnWidth(i, widths[i] * 256);
                }

                sheet.setAutoFilter(new CellRangeAddress(
                        3, Math.max(3, rowIndex - 1), 0, columns.length - 1));
            }

            writeMetadata(wb, layout, title, header, usedNames);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeMetadata(
            Workbook wb, Layout layout, CellStyle title,
            CellStyle header, Set<String> usedNames
    ) {
        Sheet sheet = wb.createSheet(
                uniqueSheetName("XML Metadata", usedNames));

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Original XML Metadata");
        titleRow.getCell(0).setCellStyle(title);

        Row groupHeading = sheet.createRow(2);
        groupHeading.createCell(0).setCellValue("GROUP Attribute");
        groupHeading.createCell(1).setCellValue("Value");
        groupHeading.getCell(0).setCellStyle(header);
        groupHeading.getCell(1).setCellStyle(header);

        int rowIndex = 3;

        for (Map.Entry<String, String> entry
                : layout.groupAttributes().entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }

        rowIndex++;

        Row xmlHeading = sheet.createRow(rowIndex++);
        xmlHeading.createCell(0).setCellValue("Original XML");
        xmlHeading.getCell(0).setCellStyle(header);

        for (String line : layout.xmlLines()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(line);
        }

        sheet.setColumnWidth(0, 100 * 256);
        sheet.setColumnWidth(1, 40 * 256);
        sheet.createFreezePane(0, 3);
    }

    private String uniqueSheetName(String requested, Set<String> used) {
        String base = requested == null || requested.isBlank()
                ? "Layout" : requested;

        base = base.replaceAll("[\\\\\\\\/?*\\\\[\\\\]:]", "_");
        if (base.length() > 31) base = base.substring(0, 31);

        String candidate = base;
        int suffix = 2;

        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            String ending = " (" + suffix++ + ")";
            int maxBase = 31 - ending.length();
            candidate = base.substring(0, Math.min(base.length(), maxBase))
                    + ending;
        }

        return candidate;
    }
}
