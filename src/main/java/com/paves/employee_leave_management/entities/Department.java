package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "department")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"employees", "groups"})
@EqualsAndHashCode(exclude = {"employees", "groups"})
public class Department {

    @Id
    @Column(name = "department_id")
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = "DEP" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    // Head of the department (for approval flows)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_id")
    private Employee head;

    // Employees in this department
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Employee> employees;

    // Groups / SubDepartments
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Group> groups;
}
