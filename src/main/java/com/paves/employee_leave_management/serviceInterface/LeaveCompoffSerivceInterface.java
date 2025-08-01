package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveStatusCompoff;

import java.util.List;

public interface LeaveCompoffSerivceInterface {
    void requestCompoff(LeaveCompoffRequestDTO dto);
    void approveCompoff(Long compoffId);
    void rejectCompoff(Long compoffId);
    List<LeaveCompoff> getCompoffsByEmployee(String employeeId);
    List<LeaveCompoff> getCompoffsByManagerAndStatus(String managerId, LeaveStatusCompoff status);
    List<LeaveCompoff> getPendingCompoffsForManager(String managerId);
}
