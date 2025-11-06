package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.paves.employee_leave_management.enums.BlockStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "leave_block_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBlockMapping {

    @Id
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    @ManyToOne
    @JoinColumn(name = "leave_block_id", nullable = false)
    @JsonBackReference
    private LeaveBlock leaveBlock;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private String leaveTypeId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BlockStatus status; // e.g., "BLOCKED", "UNBLOCKED"
}
