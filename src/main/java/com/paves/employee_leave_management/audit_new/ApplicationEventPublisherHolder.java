package com.paves.employee_leave_management.audit_new;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Data;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@Data
public class ApplicationEventPublisherHolder {
    private final ApplicationEventPublisher publisher;
    @PersistenceContext
    private EntityManager entityManager;

    public ApplicationEventPublisherHolder(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public ApplicationEventPublisher getPublisher() { return publisher; }
    public EntityManager getEntityManager() { return entityManager; }
}
