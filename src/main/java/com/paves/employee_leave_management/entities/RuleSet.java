package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "rule_set")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. LEAVE_APPROVAL, HR_LEAVE_TYPE_MANAGE

    private String description;

    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RuleCondition> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApprovalStep> approvalSteps = new ArrayList<>();
}
