package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.*;

@Entity
@Data
@Table(name = "background_jobs")
public class BackgroundJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String jobId;

    private String jobType;   // e.g. CREATE_LEAVE_BALANCE
    private String status;    // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private int progress;     // percentage (0–100)

    private String details;   // optional: error message, leave type info

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


}
