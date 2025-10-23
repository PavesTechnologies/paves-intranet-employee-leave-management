package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "rule_condition")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    private String attribute; // e.g. leaveDays, makerRole, leaveType
    private String operator;  // e.g. >, ==, <, IN
    private String value;     // e.g. 3, "JUNIOR_DEV", "SICK_LEAVE"
}
