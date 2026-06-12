package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "leave_block_member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBlockMember {

    @Id
    private String id;
    @ManyToOne
    @JoinColumn(name = "leave_block_id", nullable = false)
    @JsonBackReference
    private LeaveBlock leaveBlock;
    @Column(name = "employee_id", nullable = false)
    private String employeeId; // just store the foreign system's employee ID

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }
}

