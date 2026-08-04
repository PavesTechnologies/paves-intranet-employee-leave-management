package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.ScheduledLeaveTypeUpdate;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GenderBasedLeaveServiceInterface {
    ApiResponse<Object> createGenderBaseLeave(GenderBasedLeave genderBaseLeave);
    ApiResponse<Object> updateGenderBaseLeave(GenderBasedLeave genderBaseLeave, String leaveTypeId);
    void applyScheduledGenderBasedUpdate(ScheduledLeaveTypeUpdate scheduled);
    ApiResponse<Object> deActiveGenderBaseLeaveType(String leaveTypeId, LocalDate effectiveDate);
    List<GenderBasedLeave> getAllLeaveTypes();
    Optional<GenderBasedLeave> getLeaveType(String leaveType);

    @Transactional
    public ResponseEntity<ApiResponse<Object>> createGenderBasedDirectly(
            GenderBasedLeave genderBaseLeave, Employee maker);
}
