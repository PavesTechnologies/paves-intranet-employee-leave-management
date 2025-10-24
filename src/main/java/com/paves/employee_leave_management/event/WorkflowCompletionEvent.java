package com.paves.employee_leave_management.event;

import com.paves.employee_leave_management.entities.Request;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WorkflowCompletionEvent extends ApplicationEvent {

    private final Request request;

    public WorkflowCompletionEvent(Object source, Request request) {
        super(source);
        this.request = request;
    }
}