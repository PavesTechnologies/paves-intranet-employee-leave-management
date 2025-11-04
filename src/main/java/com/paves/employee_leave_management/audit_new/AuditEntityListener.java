package com.paves.employee_leave_management.audit_new;



import com.paves.employee_leave_management.audit.SpringContext;
import com.paves.employee_leave_management.auditUtils.AuditSnapshotUtil;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;




@Component
public class AuditEntityListener {
    public AuditEntityListener() {
        try{
            SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
        }catch (Exception ignored){}
    }

    @PrePersist
    public void prePersist(Object entity){
        if(!isAuditable(entity)) return;
        publish(entity,"INSERT");
    }

    @PreUpdate
    public void preUpdate(Object entity){
        if(!isAuditable(entity)) return;
        publish(entity,"UPDATE");
    }

    @PreRemove
    public void preRemove(Object entity){
        if(!isAuditable(entity)) return;
        publish(entity,"DELETE");
    }

    private boolean isAuditable(Object entity){
        return entity != null && entity.getClass().isAnnotationPresent(Auditable.class);
    }

    private void publish(Object entity, String action){
        try{
            ApplicationEventPublisherHolder holder = SpringContext.getBean(ApplicationEventPublisherHolder.class);
            if(holder.getPublisher() == null) return;

            String entityId = null;
            try{
                Object id = holder.getEntityManager().getEntityManagerFactory()
                        .getPersistenceUnitUtil().getIdentifier(entity);
                entityId = id != null ? String.valueOf(id) : null;
            }catch (Exception ignored){}

            Object beforeSnapshot = null;
            Object afterSnapshot = null;

            var em = holder.getEntityManager();
            if("UPDATE".equalsIgnoreCase(action) || "DELETE".equalsIgnoreCase(action)){
                try{
                    if(entityId != null){
                        Object before = em.find(entity.getClass(), entityId);
                        beforeSnapshot = AuditSnapshotUtil.toFlatMap(before);
                    }
                } catch (Exception ignored) {}
            }
            if  ("INSERT".equalsIgnoreCase(action) || "UPDATE".equalsIgnoreCase(action)){
                afterSnapshot = AuditSnapshotUtil.toFlatMap(entity);
            }else if("DELETE".equalsIgnoreCase(action)){
                if (beforeSnapshot == null) beforeSnapshot = AuditSnapshotUtil.toFlatMap(entity);
            }

            String changedBy = getUserIdFromSecurityContext();

            AuditPayload payload = new AuditPayload(entity.getClass(), entityId, beforeSnapshot, afterSnapshot, action, changedBy);
            holder.getPublisher().publishEvent(new AuditEvent(this, payload));
        }catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditEntityListener.class).error("Audit publish error", e);
        }
    }

    private String getUserIdFromSecurityContext(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if(authentication != null && authentication.getPrincipal() instanceof Jwt jwt){
                Object claim = jwt.getClaim("user_id");
                return claim != null ? claim.toString():"SYSTEM";
            }
        }catch (Exception ignored){}
        return "SYSTEM";
    }
}

