package com.paves.employee_leave_management.auditUtils;


import java.util.*;

public class FieldChange {
    private final String fieldName;
    private final Object oldValue;
    private final Object newValue;
    public FieldChange(String fieldName, Object oldValue, Object newValue) {
        this.fieldName = fieldName; this.oldValue = oldValue; this.newValue = newValue;
    }
    public String getFieldName(){return fieldName;}
    public Object getOldValue(){return oldValue;}
    public Object getNewValue(){return newValue;}
}
