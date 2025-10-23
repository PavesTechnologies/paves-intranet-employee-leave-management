package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delegation_rule")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelegationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false)
    private UUID delegateUserId;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;
}
