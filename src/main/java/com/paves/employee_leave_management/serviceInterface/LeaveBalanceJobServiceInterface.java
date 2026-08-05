package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import com.paves.employee_leave_management.entities.LeaveType;

import java.util.List;

public interface LeaveBalanceJobServiceInterface {
    void processLeaveBalancesAsync(String jobId, String leaveTypeId);
    LeaveBalance createSingleBalance(Employee employee, LeaveType leaveType);
    void updateJobStatus(String jobId, LeaveBalanceJob.JobStatus status,
                                int total, int processed);
    void updateProgress(String jobId, int processed, int percentage);
    void markCompleted(String jobId, int processed);
    void markFailed(String jobId, String errorMessage);
    void rollback(String jobId, String leaveTypeId,
                         List<String> createdBalanceIds, String errorMessage);
    LeaveBalanceJob getJobStatus(String jobId);

    // Atomically claims (PENDING, or RUNNING-but-stale) jobs so they can be resumed —
    // safe to call from multiple triggers (startup listener, periodic sweep, multiple nodes)
    // since each claim is guarded by the entity's @Version optimistic lock.
    List<LeaveBalanceJob> claimStuckJobs();
}
