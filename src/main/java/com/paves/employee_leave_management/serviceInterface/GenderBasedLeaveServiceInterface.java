package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Optional;

public interface GenderBasedLeaveServiceInterface {
    ApiResponse<Object> createGenderBaseLeave(GenderBasedLeave genderBaseLeave);
    ApiResponse<Object> updateGenderBaseLeave(GenderBasedLeave genderBaseLeave, String leaveTypeId);
    ApiResponse<Object> deActiveGenderBaseLeaveType(String leaveTypeId, LocalDate effectiveDate);
    ApiResponse<Object> getAllLeaveTypes();
    Optional<GenderBasedLeave> getLeaveType(String leaveType);
}
