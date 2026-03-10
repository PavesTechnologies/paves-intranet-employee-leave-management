package com.paves.employee_leave_management.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UploadResponse {
    private String message;
    private int processedCount;
    private List<RowError> errors;
}
