package com.paves.employee_leave_management.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.paves.employee_leave_management.enums.BlockStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "leave_block")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveBlock {

    @Id
    private String id;

    @PrePersist
    public void generateId(){
        if (id == null){
            id = UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase();
        }
    }

    @Column(name = "manager_id", nullable = false)
    private String managerId;

    @Column(name= "project_id")
    private String projectId;

    @Column(name = "department_id")
    private String departmentId; // nullable => global or dept-scoped

//    @Column(name = "leave_type_id", nullable = false)
//    private String leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @OneToMany(mappedBy = "leaveBlock", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<LeaveBlockMember> members;

    @OneToMany(mappedBy = "leaveBlock", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<LeaveBlockLeaveType> leaveTypes;


    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockStatus status; // ACTIVE, EXPIRED, CANCELLED // ACTIVE, EXPIRED, CANCELLED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "year")
    private Integer year;
}