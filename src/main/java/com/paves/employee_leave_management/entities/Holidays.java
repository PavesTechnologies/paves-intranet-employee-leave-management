package com.paves.employee_leave_management.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "holidays",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"holiday_date", "state", "year"})
        })
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
    private int year;
}
