package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

//    private UUID createdBy;
    @Column(name="created_by")
    private String createdBy;

    private String requestType;   // e.g. LEAVE, HR_OPERATION
    private String operationType; // e.g. APPLY, ADD_LEAVE_TYPE

    private UUID targetGroupId;   // optional
    private String leaveType;
    private Integer totalDays;

    private String status;// PENDING, APPROVED, REJECTED

    private String targetEntityId;

    @Lob
    private String makerAttributes; // JSON string of context info

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

}
