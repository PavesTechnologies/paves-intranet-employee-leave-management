package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "leave_block_leave_type")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBlockLeaveType {
    @Id
    private String id;
    @ManyToOne
    @JoinColumn(name = "leave_block_id")
    @JsonBackReference
    private LeaveBlock leaveBlock;
    @Column(name = "leave_type_id")
    private String leaveTypeId;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }
}

