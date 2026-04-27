package com.paves.employee_leave_management.audit_tables;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "holidays_audit")
@Data
public class HolidaysAudit extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long holidayId;
    private String holidayName;
    private LocalDate holidayDate;
    private String holidayDescription;
    private String type;
    private String state;
    private String country;
    private int year;
}