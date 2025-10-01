package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.RequestStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for returning ApprovalRequest data to the client.
 * This prevents serialization issues with Hibernate proxies.
 */
@Setter
@Getter
public class ApprovalRequestResponseDto {

    private Long id;
    private ActionType actionType;
    private RequestStatus status;
    private String payload; // The raw JSON payload for the frontend to parse
    private String makerName;
    private LocalDateTime createdAt;

    // Getters and Setters

}
