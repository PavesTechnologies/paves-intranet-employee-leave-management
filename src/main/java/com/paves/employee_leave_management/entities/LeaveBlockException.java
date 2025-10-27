package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Entity
@Table(name = "leave_block_exception")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveBlockException {

    @Id
    private UUID id;

    @Column(name = "leave_block_id", nullable = false)
    private UUID leaveBlockId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;
}
