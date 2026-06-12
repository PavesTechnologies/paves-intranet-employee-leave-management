package com.paves.employee_leave_management.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.entities.AuditTrail;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceUpdateRequest;
import com.paves.employee_leave_management.repo.AuditTrailRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditTrailRepo auditTrailRepository;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(com.paves.employee_leave_management.audit.Auditable)")
    @Transactional
    public Object auditServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Object dto = args.length > 0 ? args[0] : null;

        Object oldEntity = null;

        // Fetch old values BEFORE method execution
        if (dto instanceof LeaveBalanceUpdateRequest request) {
            oldEntity = leaveBalanceRepo.findByEmployeeEmployeeIdWithLeaveType(request.getEmployeeId());
        } else if (dto != null) {
            // Generic: attempt to load entity from DB using "id" field
            try {
                var idField = dto.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                Object idValue = idField.get(dto);
                if (idValue != null) {
                    // Implement a generic repository fetch if needed
                    // oldEntity = genericRepo.findById(idValue).orElse(null);
                }
            } catch (NoSuchFieldException ignored) {
            }
        }

        // Execute the original method
        Object result = joinPoint.proceed();

        // Prepare audit trail
        AuditTrail audit = new AuditTrail();
        audit.setTableName(getTableName(dto));
        audit.setActionType(AuditTrail.ActionType.UPDATE);
        audit.setPerformedBy(extractPerformedBy(dto));
        audit.setChangedAt(LocalDateTime.now());
        audit.setRecordId(extractRecordId(dto, oldEntity));

        // Extract old and new values
        Map<String, Object> oldValuesMap = extractOldValues(dto, oldEntity);
        Map<String, Object> newValuesMap = extractNewValues(dto);

        audit.setOldValues(objectMapper.writeValueAsString(oldValuesMap));
        audit.setNewValues(objectMapper.writeValueAsString(newValuesMap));

        auditTrailRepository.save(audit);

        return result;
    }

    private String getTableName(Object dto) {
        if (dto instanceof LeaveBalanceUpdateRequest) return "leave_balance";
        return dto != null ? dto.getClass().getSimpleName() : "UNKNOWN_TABLE";
    }

    private String extractPerformedBy(Object dto) {
        if (dto instanceof LeaveBalanceUpdateRequest req) return String.valueOf(req.getPerformedBy());
        return "UNKNOWN_USER";
    }

    private String extractRecordId(Object dto, Object oldEntity) {
        if (dto instanceof LeaveBalanceUpdateRequest req) return req.getEmployeeId();
        try {
            if (dto != null) {
                var idField = dto.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                return String.valueOf(idField.get(dto));
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN_ID";
    }

    // --------------------- OLD & NEW VALUES ---------------------

    private Map<String, Object> extractOldValues(Object source, Object oldEntity) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (source == null || oldEntity == null) return map;

        try {
            if (source instanceof LeaveBalanceUpdateRequest dto && oldEntity instanceof List<?> oldList) {
                List<LeaveBalance> oldBalances = oldList.stream()
                        .filter(l -> l instanceof LeaveBalance)
                        .map(l -> (LeaveBalance) l)
                        .collect(Collectors.toList());

                dto.getBalances().forEach(bal -> {
                    oldBalances.stream()
                            .filter(old -> old.getLeaveType() != null &&
                                    old.getLeaveType().getLeaveTypeId().equals(bal.getLeaveTypeId()) &&
                                    old.getYear() == bal.getYear())
                            .findFirst()
                            .ifPresent(old -> map.put(bal.getLeaveTypeId() + "-" + bal.getYear(), old.getRemainingLeaves()));
                });
            } else {
                // Generic entity reflection
                for (var field : source.getClass().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    field.setAccessible(true);
                    Object value = null;
                    if (oldEntity != null) {
                        try {
                            var compareField = oldEntity.getClass().getDeclaredField(field.getName());
                            compareField.setAccessible(true);
                            value = compareField.get(oldEntity);
                        } catch (NoSuchFieldException ignored) {
                        }
                    }
                    if (value != null) map.put(field.getName(), value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }

    private Map<String, Object> extractNewValues(Object source) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (source == null) return map;

        try {
            if (source instanceof LeaveBalanceUpdateRequest dto) {
                dto.getBalances().forEach(bal ->
                        map.put(bal.getLeaveTypeId() + "-" + bal.getYear(), bal.getRemainingLeaves())
                );
            } else {
                for (var field : source.getClass().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    field.setAccessible(true);
                    Object value = field.get(source);
                    map.put(field.getName(), value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }
}
