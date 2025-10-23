package com.paves.employee_leave_management.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "group_subdepartment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"employees"})
@EqualsAndHashCode(exclude = {"employees"})
public class Group {

    @Id
    @Column(name = "group_id")
    private String id;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = "GRP" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        }
    }

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    // Parent Department
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // Head of this group / subdepartment
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_id")
    private Employee head;

    // Employees belonging to this group
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Employee> employees;
}
