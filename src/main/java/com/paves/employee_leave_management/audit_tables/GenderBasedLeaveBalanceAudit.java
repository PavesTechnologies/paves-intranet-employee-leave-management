package com.paves.employee_leave_management.audit_tables;


import com.paves.employee_leave_management.entities.GenderBasedLeave;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GenderBasedLeaveBalanceAudit extends BaseAuditEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeId;
    private String leaveTypeId;
    private Integer totalEntitledDays;
    private Integer usedDays;
    private Integer remainingDays;
    private Integer year;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer timesUsed;
}
