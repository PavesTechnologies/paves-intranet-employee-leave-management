
package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveRequest;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;
import com.paves.employee_leave_management.repo.LeaveRequestRepo;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service("permissionService")
public class PermissionService {

    private final EmployeeRepo employeeRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final LeaveCompoffRepo leaveCompoffRepo;

    public PermissionService(EmployeeRepo employeeRepo, LeaveRequestRepo leaveRequestRepo, LeaveCompoffRepo leaveCompoffRepo) {
        this.employeeRepo = employeeRepo;
        this.leaveRequestRepo = leaveRequestRepo;
        this.leaveCompoffRepo = leaveCompoffRepo;
    }

    private String getAuthenticatedEmployeeId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("user_id");
        }
        throw new IllegalArgumentException("Authentication principal is not a JWT");
    }

    public boolean isOwner(Authentication authentication, String employeeId) {
        String authenticatedEmployeeId = getAuthenticatedEmployeeId(authentication);
        return authenticatedEmployeeId != null && authenticatedEmployeeId.equals(employeeId);
    }

    public boolean isOwnerOfLeaveRequest(Authentication authentication, String leaveId) {
        String authenticatedEmployeeId = getAuthenticatedEmployeeId(authentication);
        LeaveRequest leaveRequest = leaveRequestRepo.findById(leaveId).orElse(null);
        return authenticatedEmployeeId != null && leaveRequest != null && leaveRequest.getEmployee().getEmployeeId().equals(authenticatedEmployeeId);
    }

    public boolean isManager(Authentication authentication, String employeeId) {
        String authenticatedEmployeeId = getAuthenticatedEmployeeId(authentication);
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        return authenticatedEmployeeId != null && employee != null && 
               employee.getManager() != null && 
               employee.getManager().getEmployeeId().equals(authenticatedEmployeeId);
    }

    public boolean isManagerOfLeaveRequest(Authentication authentication, String leaveId) {
        String authenticatedEmployeeId = getAuthenticatedEmployeeId(authentication);
        LeaveRequest leaveRequest = leaveRequestRepo.findById(leaveId).orElse(null);
        return authenticatedEmployeeId != null && 
               leaveRequest != null && 
               leaveRequest.getEmployee().getManager() != null &&
               leaveRequest.getEmployee().getManager().getEmployeeId().equals(authenticatedEmployeeId);
    }

    public boolean isOwnerOfCompoffRequest(Authentication authentication, Long compoffId) {
        String authenticatedEmployeeId = getAuthenticatedEmployeeId(authentication);
        LeaveCompoff leaveCompoff = leaveCompoffRepo.findById(compoffId).orElse(null);
        return authenticatedEmployeeId != null && leaveCompoff != null && leaveCompoff.getEmployee().getEmployeeId().equals(authenticatedEmployeeId);
    }

    public boolean isManagerOfCompoffRequest(Authentication authentication, Long compoffId) {
        String authenticatedEmployeeId = getAuthenticatedEmployeeId(authentication);
        LeaveCompoff leaveCompoff = leaveCompoffRepo.findById(compoffId).orElse(null);
        return authenticatedEmployeeId != null && 
               leaveCompoff != null && 
               leaveCompoff.getEmployee().getManager() != null && 
               leaveCompoff.getEmployee().getManager().getEmployeeId().equals(authenticatedEmployeeId);
    }
}
