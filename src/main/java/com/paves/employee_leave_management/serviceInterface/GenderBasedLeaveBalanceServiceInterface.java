package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.UploadResponse;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.GenderBasedLeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.globalExceptionHandler.LeaveBalanceExceptionHandler;
import jakarta.transaction.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface GenderBasedLeaveBalanceServiceInterface {
    void createLeaveBalanceForAllEmployees(GenderBasedLeave genderBasedLeave);
    void updateLeaveBalanceForEmployee(GenderBasedLeave genderBasedLeave, String employeeId);
    List<GenderBasedLeaveBalance> getCurrentYearBalances(String employeeId);
    GenderBasedLeaveBalance getCurrentYearBalancesForEmployee(String employeeId);
    public void updateLeaveBalanceAfterApproval(String employeeId, String leaveTypeId, double approvedDays, int year);
    void updateLeaveBalanceAfterRejected(String employeeId, String leaveTypeId, double rejectedDays, int year);
    byte[] generateTemplate() throws IOException;
    UploadResponse handleAccruedUpload(MultipartFile file, String username) throws IOException;
}
