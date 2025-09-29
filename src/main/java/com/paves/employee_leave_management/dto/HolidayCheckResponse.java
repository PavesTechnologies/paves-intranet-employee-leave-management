package com.paves.employee_leave_management.dto;

import java.time.LocalDate;

/**
 * DTO for holiday check response
 */
public record HolidayCheckResponse(
        String status,      // "yes" if holiday, "no" otherwise
        String message,
        LocalDate date// Holiday name or "Not a holiday"
) {
    // Record automatically provides:
    // - Constructor
    // - Getters
    // - equals(), hashCode(), toString()
}
