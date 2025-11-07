package com.paves.employee_leave_management.entities;


import com.paves.employee_leave_management.audit.AuditEntityListener;
import com.paves.employee_leave_management.enums.HolidayType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "holidays",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"holiday_date", "state", "year"})
        })
@EntityListeners(AuditEntityListener.class)
public class Holidays {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long holidayId;

    @Column(nullable = false)
    private String holidayName;

    @Column(nullable = false)
    private LocalDate holidayDate;

    private String holidayDescription;

    @Enumerated(EnumType.STRING)
    private HolidayType type;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private int year;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "last_updated_at", insertable = false)
    private LocalDateTime lastUpdatedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
