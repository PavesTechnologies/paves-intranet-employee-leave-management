package com.paves.employee_leave_management.dto;

// 2. ValidationResultDTO.java (in dto package)
//package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResultDTO {
    private boolean isValid;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private String employeeId;
    private String employeeName;
    private Integer availableBalance;
    private Integer requestedDays;

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
        this.isValid = false;
    }
}