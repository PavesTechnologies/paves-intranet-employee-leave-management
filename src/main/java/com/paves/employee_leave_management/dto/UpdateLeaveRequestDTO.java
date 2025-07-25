package com.paves.employee_leave_management.dto;

import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateLeaveRequestDTO {
        private String managerId;
        private String leaveTypeId;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;

}
