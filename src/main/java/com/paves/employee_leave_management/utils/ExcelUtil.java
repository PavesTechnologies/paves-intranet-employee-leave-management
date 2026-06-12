package com.paves.employee_leave_management.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

public class ExcelUtil {
    public static String getString(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);

        if (cell == null) {
            throw new RuntimeException("Cell is empty at column " + cellIndex);
        }

        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    public  static int getInt(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);

        if (cell == null) {
            throw new RuntimeException("Cell is empty at column " + cellIndex);
        }

        return (int) cell.getNumericCellValue();
    }

    public static  double getDouble(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);

        if (cell == null) {
            throw new RuntimeException("Cell is empty at column " + cellIndex);
        }

        return cell.getNumericCellValue();
    }


}
