package com.paves.employee_leave_management.dto;

// 2. ValidationResultDTO.java (in dto package)
//package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResultDTO {
    private boolean isValid;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> messages = new ArrayList<>();
    private String employeeId;
    private String employeeName;
    private float availableBalance;
    private float requestedDays;
    private String leaveId;

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
        this.isValid = false;
    }

    public void addMessage(String message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
    }
}