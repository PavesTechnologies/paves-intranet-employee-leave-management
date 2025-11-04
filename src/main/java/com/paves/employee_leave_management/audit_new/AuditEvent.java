package com.paves.employee_leave_management.audit_new;

import org.springframework.context.ApplicationEvent;

public class AuditEvent extends org.springframework.context.ApplicationEvent {
    private final AuditPayload payload;
    public AuditEvent(Object source, AuditPayload payload) {
        super(source);
        this.payload = payload;
    }
    public AuditPayload getPayload() { return payload; }
}
