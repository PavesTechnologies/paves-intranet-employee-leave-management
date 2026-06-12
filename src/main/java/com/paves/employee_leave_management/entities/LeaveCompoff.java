package com.paves.employee_leave_management.entities;


import com.paves.employee_leave_management.enums.LeaveStatusCompoff;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_compoff")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LeaveCompoff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idleave_compoff")
    private Long idleaveCompoff;

    @Column(name = "employee_id")
    private String employeeId;

    @ManyToOne
    @JoinColumn(name = "employee_id", referencedColumnName = "employee_id", insertable = false, updatable = false)
    private Employee employee;

    @Column(name = "manager_id")
    private String managerId;

    @Column(name = "worked_date")
    private LocalDate workedDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private double duration;

    private String note;

    private String file;

    @Column(name = "start_session", columnDefinition = "TEXT")
    private String startSession;

    @Column(name = "end_session", columnDefinition = "TEXT")
    private String endSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LeaveStatusCompoff status;

    @Column(name = "action_date")
    private LocalDate actionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;


    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "last_updated_at", insertable = false)
    private LocalDateTime lastUpdatedAt;

}
