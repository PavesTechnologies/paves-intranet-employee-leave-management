package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.AllLeaveTypesListResponseDTO;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.LeaveTypeDTO;
import com.paves.employee_leave_management.dto.LeaveTypeIdDTO;
import com.paves.employee_leave_management.entities.LeaveType;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;


public interface LeaveTypeServiceInterface {
    public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType);

    public ResponseEntity<AllLeaveTypesListResponseDTO> getAllLeaveTypes();

    //    public ResponseEntity<ApiResponse<LeaveType>> updateLeaveType(String leaveTypeId);
    @Transactional
    ApiResponse<LeaveType> updateLeaveType(LeaveType updatedLeaveType, String leaveTypeId);

    ResponseEntity<LeaveType> getLeaveTypeById(String leaveTypeId);

    ResponseEntity<String> deleteLeaveType(String leaveTypeId);

    public ResponseEntity<String> deActiveLeaveType(String leaveTypeId, LocalDate effectiveDate);

//    void uploadDocument(String leaveTypeId, MultipartFile file) throws Exception;
//
//    byte[] viewDocument(String leaveTypeId, String fileType) throws Exception;
//
//    void deleteDocument(String leaveTypeId) throws Exception;

    String getMimeType(String fileType) throws Exception;

    public List<LeaveTypeIdDTO> getAllLeaveTypeIds();
}
