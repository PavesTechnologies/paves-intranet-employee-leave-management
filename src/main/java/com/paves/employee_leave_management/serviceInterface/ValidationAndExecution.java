package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Component
@Configuration
public interface ValidationAndExecution {
    ApiResponse<Object> validateGenderBaseLeave(GenderBasedLeave genderBaseLeave, Employee maker, String makerRole);
    ApiResponse<Object> updateValidateGenderBaseLeaveBalance(GenderBasedLeaveBalance genderBaseLeave, Employee maker, String makerRole);
}
