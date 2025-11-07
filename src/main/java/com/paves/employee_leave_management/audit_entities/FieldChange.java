package com.paves.employee_leave_management.audit_entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FieldChange {
    private final String fieldName;
    private final Object oldValue;
    private final Object newValue;
}