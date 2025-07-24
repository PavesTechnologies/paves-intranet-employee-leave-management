//package com.paves.employee_leave_management.entities;
//
//import com.fasterxml.jackson.annotation.JsonIgnore;
//
//import com.fasterxml.jackson.annotation.JsonManagedReference;
//import jakarta.persistence.*;
//import lombok.*;
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.UUID;
//
//// Employee Entity
//@Entity
//@Table(name = "employee")
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//@ToString(exclude = {"subordinates", "leaveRequests", "approvedRequests", "leaveBalances",""})
//@EqualsAndHashCode(exclude = {"subordinates", "leaveRequests", "approvedRequests", "leaveBalances"})
//public class Employee {
//
//    @Id
//    @Column(name = "employee_id")
//    private String employeeId;
//
//    @PrePersist
//    public void generateId(){
//        if(employeeId == null) {
//            employeeId = "PAVEMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
//        }
//    }
//
//    @Column(name = "first_name", length = 50, nullable = false)
//    private String firstName;
//
//    @Column(name = "last_name", length = 50, nullable = false)
//    private String lastName;
//
//    @Column(name = "email", length = 100, nullable = false, unique = true)
//    private String email;
//
//    @Column(name = "gender", length = 50, nullable = false, unique = true)
//    private String gender;
//
//    @Column(name = "phone", length = 15)
//    private String phone;
//
//    @Column(name = "hire_date", nullable = false)
//    private LocalDate hireDate;
//
//    @Column(name = "salary", precision = 10, scale = 2)
//    private BigDecimal salary;
//
//    @Column(name = "job_title", length = 100)
//    private String jobTitle;
//
//    @ManyToOne
//    @JoinColumn(name = "manager_id")
//    @JsonManagedReference
//    private Employee manager;
//
//    @OneToMany(mappedBy = "manager", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    @JsonIgnore
//    private List<Employee> subordinates;
//
//    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    private List<LeaveRequest> leaveRequests;
//
//    @OneToMany(mappedBy = "approvedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    private List<LeaveRequest> approvedRequests;
//
//    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    @JsonIgnore
//    private List<LeaveBalance> leaveBalances;
//
//    // Custom constructor for essential fields
//    public Employee(String firstName, String lastName, String email, LocalDate hireDate) {
//        this.firstName = firstName;
//        this.lastName = lastName;
//        this.email = email;
//        this.hireDate = hireDate;
//    }
//
////     Utility method
//    public String getFullName() {
//        return firstName + " " + lastName;
//    }
//}



package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Employee Entity
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
        "manager" // ✅ Prevents recursive loop in toString()
})
@EqualsAndHashCode(exclude = {
        "subordinates",
        "leaveRequests",
        "approvedRequests",
        "leaveBalances",
        "manager" // ✅ Prevents recursive loop in equals/hashCode
})
public class Employee {

    @Id
    @Column(name = "employee_id")
    @JsonIgnore
    private String employeeId;

    @PrePersist
    public void generateId(){
        if(employeeId == null) {
            employeeId = "PAVEMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "gender", length = 50, nullable = false, unique = true)
    private String gender;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "salary", precision = 10, scale = 2)
    private BigDecimal salary;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "manager_id")
    @JsonManagedReference
    private Employee manager;

    @OneToMany(mappedBy = "manager", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Employee> subordinates;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LeaveRequest> leaveRequests;

    @Column(name="password", length = 10)
    private String password;

    @OneToMany(mappedBy = "approvedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LeaveRequest> approvedRequests;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<LeaveBalance> leaveBalances;

    // Custom constructor for essential fields
    public Employee(String firstName, String lastName, String email, LocalDate hireDate) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.hireDate = hireDate;
    }

    // Utility method
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
