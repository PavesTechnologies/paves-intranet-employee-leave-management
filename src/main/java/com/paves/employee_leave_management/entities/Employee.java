package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paves.employee_leave_management.enums.EmployeeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Employee {

    @Id
    @Column(name = "employee_id")
    @ToString.Include
    @EqualsAndHashCode.Include
    private String employeeId;

    @Column(name = "employee_uuid", unique = true)
    private String employeeUuid;

    @Column(name = "first_name", length = 50, nullable = false)
    @ToString.Include
    private String firstName;
    @Column(name = "last_name", length = 50, nullable = false)
    @ToString.Include
    private String lastName;
    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;
    @Column(name = "gender", length = 10, nullable = false)
    private String gender;
    @Column(name = "phone", length = 15)
    private String phone;
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;
    @Column(name = "salary", precision = 10, scale = 2)
    private BigDecimal salary;
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "role", length = 50)
    private String role;
    // 🔹 Self-reference to represent manager
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @JsonIgnore
    private Employee manager;

    @Column(name = "manager_id", insertable = false, updatable = false)
    private String managerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_id")
    @JsonIgnore
    private Employee hr;

    @Column(name = "hr_id", insertable = false, updatable = false)
    private String hrId;

    // 🔹 Reverse mapping: manager → subordinates
    @OneToMany(mappedBy = "manager", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Employee> subordinates;
    // 🔹 HR Administrator (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_administrator_id")
    @JsonIgnore
    private Employee hrAdministrator;
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveRequest> leaveRequests;

    // 🔹 Department linkage
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "department_id")
//    @JsonIgnore
//    private Department department;
    @OneToMany(mappedBy = "approvedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveRequest> approvedRequests;
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveBalance> leaveBalances;
    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "status", nullable = false)
    private EmployeeStatus status;

    @PrePersist
    public void generateId() {
        if (employeeId == null) {
            employeeId = "PAVEMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    // Convenience method
    public String getFullName() {
        return firstName + " " + lastName;
    }


}
