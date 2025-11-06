package com.paves.employee_leave_management.auditUtils;

import java.util.*;

public class DiffUtil {
    public static List<FieldChange> diff(Map<String, Object> before, Map<String, Object> after) {
        List<FieldChange> changes = new ArrayList<>();
        if (after == null) return changes;
        before = before == null ? Collections.emptyMap() : before;
        for (String key : after.keySet()) {
            Object a = after.get(key);
            Object b = before.get(key);
            if (!Objects.equals(a, b)) {
                changes.add(new FieldChange(key, b, a));
            }
        }
        return changes;
    }
}
