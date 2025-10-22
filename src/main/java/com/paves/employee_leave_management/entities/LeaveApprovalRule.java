package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.HierarchyMapping;
import com.paves.employee_leave_management.enums.LeaveApproverType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class LeaveApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Assuming you have a LeaveType entity. If not, this can be a String or Long.
    private Long leaveTypeId;

    private int level;

    @Enumerated(EnumType.STRING)
    private LeaveApproverType approverType;

    @Enumerated(EnumType.STRING)
    private HierarchyMapping hierarchyMapping;

    private String fixedApproverId;

    private String conditionExpression;

    private boolean isParallel;
}
