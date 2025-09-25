package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobProgressDTO implements Serializable {
    private String jobId;
    private int progress;
    private String status;
    private String details;
}
