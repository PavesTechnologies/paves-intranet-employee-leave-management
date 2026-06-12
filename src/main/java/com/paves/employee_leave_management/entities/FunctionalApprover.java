package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "functional_approvers")
public class FunctionalApprover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private Employee approver;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Employee getApprover() {
        return approver;
    }

    public void setApprover(Employee approver) {
        this.approver = approver;
    }
}
