package com.paves.employee_leave_management.entities;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_stage")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    private Integer level;
//    private UUID approverId;
    @Column(name = "approver_id")
    private String approverId;

    private String status; // PENDING, APPROVED, REJECTED, SKIPPED

    private LocalDateTime assignedAt;
    private LocalDateTime actionAt;
}
