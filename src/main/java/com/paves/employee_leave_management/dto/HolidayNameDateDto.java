package com.paves.employee_leave_management.dto;

import lombok.*;

import java.time.LocalDate;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class HolidayNameDateDto {
    private String holidayName;
    private LocalDate holidayDate;
}
