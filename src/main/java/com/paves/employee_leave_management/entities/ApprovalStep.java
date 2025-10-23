package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import com.paves.employee_leave_management.enums.*;

@Entity
@Table(name = "approval_step")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    private Integer level; // 1, 2, 3...

    @Enumerated(EnumType.STRING)
    private ApproverType approverType;

    private String approverValue; // e.g. ROLE:HR_MANAGER, FIXED_USER_ID, DEPT_HEAD

    @Enumerated(EnumType.STRING)
    private ApprovalMode approvalMode; // SEQUENTIAL / PARALLEL
}
