package com.paves.employee_leave_management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.repo.AuditTrailRepo;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private AuditTrailRepo auditLogsRepo;
    private ObjectMapper objectMapper;

    public AuditLogService(AuditTrailRepo auditLogsRepo, ObjectMapper objectMapper) {
        this.auditLogsRepo = auditLogsRepo;
        this.objectMapper = objectMapper;
    }

//    public void logAudit(String action, String entityName, String performedBy,
//                         Object oldValue, Object newValue, String reason, String entityID){
//        try{
//            AuditLogs logs = new AuditLogs();
//            logs.setAction(action);
//            logs.setEntityName(entityName);
//            logs.setEntityId(entityID != null ? entityID.toString(): null );
//            logs.setPerformedBy(performedBy);
//            logs.setBeforeChange(oldValue != null ? objectMapper.writeValueAsString(oldValue): null);
//            logs.setAfterChange(newValue != null ? objectMapper.writeValueAsString(newValue): null);
//            logs.setReason(reason);
//
//            auditLogsRepo.save(logs);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//    }
}
