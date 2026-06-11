package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.*;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveType;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public interface LeaveTypeServiceInterface {

    @Transactional
    ResponseEntity<ApiResponse<Object>> createDirectly(LeaveType leaveType, AdminMaker maker);

    public List<Map<String, String>> getLeaveTypes();
    public ApiResponse<LeaveType> addLeaveType(LeaveType leaveType);

    public AllLeaveTypesListResponseDTO getAllLeaveTypes();

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
