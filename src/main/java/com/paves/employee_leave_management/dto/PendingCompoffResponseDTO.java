package com.paves.employee_leave_management.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PendingCompoffResponseDTO {
    private Long idleaveCompoff;
    private String employeeId;
    private String employeeName;
    private LocalDate startDate;
    private LocalDate endDate;
    //    private Double days;
//    private Double halfDays;
    private Double duration;
    private String note;
    private String startSession;
    private String endSession;
    private String status;
    private LocalDate actionDate;
    private LocalDate expiryDate;
}
