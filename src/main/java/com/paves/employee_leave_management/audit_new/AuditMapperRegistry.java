package com.paves.employee_leave_management.audit_new;

import com.paves.employee_leave_management.audit_entities.BaseAuditEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Component
public class AuditMapperRegistry {
    private final Map<Class<?>, BiFunction<Object, Object, BaseAuditEntity>> mappers = new HashMap<>();
//    private final ObjectMapper objectMapper;

//    public AuditMapperRegistry(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//
//        mappers.put(LeaveType.class,(after, before)-> mapLeaveType(after, before));
//
//    }

//    private static LeaveTypeAuditLog mapLeaveType(Object afterObj, Object beforeObj){
//        var after = (LeaveType)afterObj;
//        var audit = new LeaveTypeAuditLog();
//
//        audit.setLeaveTypeId(after.getLeaveTypeId());
//        audit.se(after.getLeaveType());
//        audit.setLeaveType(after.getLeaveType());
//    }

}
