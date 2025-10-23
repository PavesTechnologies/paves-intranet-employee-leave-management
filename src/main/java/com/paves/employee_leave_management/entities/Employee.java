package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "employee")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {
        "subordinates",
        "leaveRequests",
        "approvedRequests",
        "leaveBalances",
        "manager",
        "hrAdministrator",
        "department",
        "group"
})
@EqualsAndHashCode(exclude = {
        "subordinates",
        "leaveRequests",
        "approvedRequests",
        "leaveBalances",
        "manager",
        "hrAdministrator",
        "department",
        "group"
})
public class Employee {

    @Id
    @Column(name = "employee_id")
    private String employeeId;

    @PrePersist
    public void generateId() {
        if (employeeId == null) {
            employeeId = "PAVEMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "gender", length = 20, nullable = false)
    private String gender;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "salary", precision = 10, scale = 2)
    private BigDecimal salary;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // =============================
    // 🧩 Hierarchy & Role Structure
    // =============================

    @Column(name = "role", length = 50, nullable = false)
    private String role; // Example: "JUNIOR_DEV", "TEAM_LEAD", "HR_MANAGER"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @JsonIgnore
    private Employee manager;

    @OneToMany(mappedBy = "manager", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Employee> subordinates;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_administrator_id")
    @JsonIgnore
    private Employee hrAdministrator;

    // =============================
    // 🏖 Leave & Approval Relations
    // =============================

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveRequest> leaveRequests;

    @OneToMany(mappedBy = "approvedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveRequest> approvedRequests;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveBalance> leaveBalances;

    @Column(name = "password", length = 255)
    private String password;

    // =============================
    // 🧠 Utility Methods
    // =============================

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
