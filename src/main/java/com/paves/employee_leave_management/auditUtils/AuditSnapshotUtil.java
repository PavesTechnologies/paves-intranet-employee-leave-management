package com.paves.employee_leave_management.auditUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuditSnapshotUtil {

    private static final ObjectMapper M = new ObjectMapper();

    /**
     * Returns a LinkedHashMap mapping fieldName -> value for scalar/primitive fields.
     * Skips: collections, relations (JPA annotations), static fields, transient fields, large blobs.
     */
    public static Map<String, Object> toFlatMap(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (entity == null) return map;

        Class<?> cls = entity.getClass();
        // walk fields including superclasses
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.isAnnotationPresent(Transient.class)) continue;

                // skip JPA relationships
                if (field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(ManyToMany.class)
                        || field.isAnnotationPresent(OneToOne.class)) {
                    continue;
                }
                field.setAccessible(true);

                try {
                    Object value = field.get(entity);
                    // skip large binary/blob if present by type
                    if (value != null && value.getClass().isArray() && value.getClass().getComponentType() == byte.class) {
                        continue;
                    }
                    // convert complex objects (like nested DTOs) to canonical JSON strings
                    Object normalized = normalizeValue(value);
                    map.put(field.getName(), normalized);
                } catch (IllegalAccessException ignored) {
                }
            }
            cls = cls.getSuperclass();
        }
        return map;
    }

    private static Object normalizeValue(Object v) {
        if (v == null) return null;
        if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;
        try {
            // canonical JSON string for complex types
            return M.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }
}
