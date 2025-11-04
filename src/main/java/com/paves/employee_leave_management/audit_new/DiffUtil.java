package com.paves.employee_leave_management.audit_new;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paves.employee_leave_management.audit_entities.FieldChange;

import java.util.*;

public class DiffUtil {

    public static final ObjectMapper M = new ObjectMapper();

    /**
     * Convert before/after objects to Map<String,Object> and compute differences.
     * It compares keys present in "after" map (so mirrored fields in entity).
     * Returns list of FieldChange for fields that changed (Objects compared via Objects.equals()).
     */
    @SuppressWarnings("unchecked")
    public static List<FieldChange> computeFieldChanges(Object before, Object after) {
        List<FieldChange> result = new ArrayList<>();
        try {
            Map<String, Object> beforeMap = before == null ? Collections.emptyMap() :
                    M.convertValue(before, Map.class);
            Map<String, Object> afterMap = after == null ? Collections.emptyMap() :
                    M.convertValue(after, Map.class);

            // iterate keys in afterMap to preserve fields present in entity
            for (String key : afterMap.keySet()) {
                Object a = normalizeValue(afterMap.get(key));
                Object b = normalizeValue(beforeMap.get(key));
                if (!Objects.equals(a, b)) {
                    result.add(new FieldChange(key, b, a));
                }
            }
        } catch (Exception e) {
            // fallback: no diffs if conversion fails
        }
        return result;
    }

    /**
     * Normalizes values for comparison: keep primitives/strings as-is; convert complex objects/collections to JSON string.
     */
    private static Object normalizeValue(Object v) {
        if (v == null) return null;
        if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;
        try {
            // convert complex objects/collections to canonical JSON string for stable comparison
            return M.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }
}