package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.ApprovalStage;
import com.paves.employee_leave_management.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
//
//public interface ApprovalStageRepo extends JpaRepository<ApprovalStage, Long> {
//    Optional<ApprovalStage> findByLeaveRequestIdAndApproverIdAndStatus(String leaveRequestId, String approverId, ApprovalStatus status);
//    Optional<ApprovalStage> findByLeaveRequest_LeaveIdAndApproverIdAndStatus(String leaveId, String approverId, ApprovalStatus status);
//    long countByLeaveRequestIdAndLevelAndStatus(String leaveRequestId, int level, ApprovalStatus status);
//
//    List<ApprovalStage> findByLeaveRequestIdAndLevel(String leaveRequestId, int level);
//}
//import com.paves.employee_leave_management.entities.ApprovalStage;
//import com.paves.employee_leave_management.enums.ApprovalStatus;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.List;
//import java.util.Optional;

public interface ApprovalStageRepo extends JpaRepository<ApprovalStage, Long> {

    // Corrected: Was findByLeaveRequestIdAndApproverIdAndStatus
    Optional<ApprovalStage> findByLeaveRequest_LeaveIdAndApproverIdAndStatus(String leaveId, String approverId, ApprovalStatus status);

    // Corrected: Was countByLeaveRequestIdAndLevelAndStatus
    long countByLeaveRequest_LeaveIdAndLevelAndStatus(String leaveId, int level, ApprovalStatus status);

    // Corrected: Was findByLeaveRequestIdAndLevel
    List<ApprovalStage> findByLeaveRequest_LeaveIdAndLevel(String leaveId, int level);
}