package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.dto.PendingCompoffResponseDTO;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.enums.LeaveStatusCompoff;

import java.util.List;

public interface LeaveCompoffSerivceInterface {
    LeaveCompoff requestCompoff(LeaveCompoffRequestDTO dto);

    LeaveCompoff approveCompoff(Long compoffId);

    LeaveCompoff rejectCompoff(Long compoffId);

    List<LeaveCompoff> getCompoffsByEmployee(String employeeId);

    List<LeaveCompoff> getCompoffsByManagerAndStatus(String managerId, LeaveStatusCompoff status);

    List<PendingCompoffResponseDTO> getPendingCompoffsForManager(String managerId);


    void cancelPendingCompoff(Long compOffId);

    LeaveCompoff cancelPendingCompOffByEmployee(Long id);

    void expireUnusedCompoffs();
}
