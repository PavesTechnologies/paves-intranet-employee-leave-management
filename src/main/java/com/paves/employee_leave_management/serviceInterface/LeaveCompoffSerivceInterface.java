package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.dto.LeaveCompoffUpdateStatusDTO;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveStatusCompoff;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;

import java.util.List;

public interface LeaveCompoffSerivceInterface {
    void requestCompoff(LeaveCompoffRequestDTO dto);
    void updateCompoffStatus(LeaveCompoffUpdateStatusDTO dto);
    List<LeaveCompoff> getCompoffsByEmployee(String employeeId);
    List<LeaveCompoff> getCompoffsByManagerAndStatus(String managerId, LeaveStatusCompoff status);
}
