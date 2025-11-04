package com.paves.employee_leave_management.audit;


import com.paves.employee_leave_management.auditRepo.*;
import com.paves.employee_leave_management.audit_tables.*;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import jakarta.persistence.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.HashSet;

//@Component
public class AuditEntityListener {

    private static ApplicationContext context;

    private static LeaveTypeRepo leaveTypeRepo;

    @Autowired
    public void setLeaveTypeRepo(LeaveTypeRepo leaveTypeRepo) {
        this.leaveTypeRepo = leaveTypeRepo;
    }


    public static void setApplicationContext(ApplicationContext ctx) {
        context = ctx;
    }

    @PrePersist
    public void prePersist(Object entity) {
        saveAudit(entity, "INSERT");
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        saveAudit(entity, "UPDATE");
    }

    @PreRemove
    public void preRemove(Object entity) {
        saveAudit(entity, "DELETE");
    }

    @SuppressWarnings("unchecked")
    private void saveAudit(Object entity, String action) {
        try {
            Long userId = getUserIdFromToken(); // default to null if unauthenticated

            BaseAuditEntity auditEntity = mapToAuditEntity(entity);
            if (auditEntity == null) return;

            auditEntity.setAction(action);
            auditEntity.setChangedBy(userId != null ? userId.toString() : "SYSTEM"); // store as String in audit table
            auditEntity.setChangedAt(LocalDateTime.now());

            ((BaseAuditRepository<BaseAuditEntity>) getRepository(auditEntity)).save(auditEntity);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private BaseAuditEntity mapToAuditEntity(Object entity) {
        // Map entity to corresponding audit entity
        if (entity instanceof com.paves.employee_leave_management.entities.LeaveRequest lr) {
            LeaveRequestAudit audit = new LeaveRequestAudit();
            BeanUtils.copyProperties(lr, audit);
            if (lr.getEmployee() != null) audit.setEmployeeId(lr.getEmployee().getEmployeeId());
            if (lr.getLeaveType() != null) audit.setLeaveTypeId(lr.getLeaveType().getLeaveTypeId());
            return audit;
        } else if (entity instanceof com.paves.employee_leave_management.entities.LeaveBalance lb) {
            LeaveBalanceAudit audit = new LeaveBalanceAudit();
            BeanUtils.copyProperties(lb, audit);
            if (lb.getEmployee() != null) audit.setEmployeeId(lb.getEmployee().getEmployeeId());
            if (lb.getLeaveType() != null) audit.setLeaveTypeId(lb.getLeaveType().getLeaveTypeId());
            return audit;
        } else if (entity instanceof com.paves.employee_leave_management.entities.Holidays h) {
            HolidaysAudit audit = new HolidaysAudit();
            BeanUtils.copyProperties(h, audit);
            if (h.getType() != null) audit.setType(h.getType().name());
            return audit;
        }
        else if(entity instanceof LeaveType lt){
            LeaveTypeAudit audit = new LeaveTypeAudit();
            BeanUtils.copyProperties(lt, audit);
            if(lt.getLeaveTypeId() != null) audit.setLeaveTypeId(lt.getLeaveTypeId());
            return audit;
        }

        // Add more mappings for other entities
        return null;
    }


    private BaseAuditRepository<? extends BaseAuditEntity> getRepository(BaseAuditEntity auditEntity) {
        if (auditEntity instanceof LeaveRequestAudit) {
            return SpringContext.getBean(LeaveRequestAuditRepository.class);
        } else if (auditEntity instanceof LeaveBalanceAudit) {
            return SpringContext.getBean(LeaveBalanceAuditRepository.class);
        } else if (auditEntity instanceof HolidaysAudit) {
            return SpringContext.getBean(HolidaysAuditRepository.class);
        } else if (auditEntity instanceof LeaveTypeAudit) {
            return SpringContext.getBean(LeaveTypeAuditRepo.class);
        }
        throw new IllegalArgumentException("No repository found for " + auditEntity.getClass());
    }


    private Long getUserIdFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            // Replace "userId" with the actual claim name in your JWT
            Object claim = jwt.getClaim("user_id");
            if (claim != null) {
                return Long.parseLong(claim.toString());
            }
        }
        return null; // fallback if not authenticated
    }

}
