package com.paves.employee_leave_management.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name="leave_compoff")
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

    @Column(name = "employee_id" , insertable = false,updatable = false)
    private String employeeId;

    @ManyToOne
    @JoinColumn(name = "employee_id", referencedColumnName = "employee_id")
    private Employee employee;

    @Column(name = "manager_id")
    private String managerId;

    @Column(name = "worked_date")
    private LocalDate workedDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private double days;

    @Column(name = "half_days")
    private Double halfDays;

    private String note;

    private String file;

    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private LeaveStatusCompoff status;

    @Column(name = "action_date")
    private LocalDate actionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

}
