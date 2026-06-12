package com.paves.employee_leave_management.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "leave_block_exception")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBlockException {

    @Id
    private UUID id;

    @Column(name = "leave_block_id", nullable = false)
    private UUID leaveBlockId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;
}
