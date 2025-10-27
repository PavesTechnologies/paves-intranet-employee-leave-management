package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_action")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private ApprovalStage stage;

//    private UUID actionBy;
    @Column(name = "action_by")
    private String actionBy;

    private String actionType; // APPROVE, REJECT, COMMENT
    private String comment;
    private LocalDateTime actionAt;
}



