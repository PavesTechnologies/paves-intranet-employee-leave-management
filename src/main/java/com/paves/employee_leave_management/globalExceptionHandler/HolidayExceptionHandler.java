package com.paves.employee_leave_management.globalExceptionHandler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class HolidayExceptionHandler extends RuntimeException {
    private String exMsg;
}
