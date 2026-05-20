package com.paves.employee_leave_management.utils;

import com.paves.employee_leave_management.enums.LeaveTypesEnum;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class UtilsMethods {
    public static String resolveLeaveLabel(String rawName) {
        if (rawName == null) return null;
        try {
            return LeaveTypesEnum.valueOf(rawName).getLabel(); // e.g. "PATERNITY_LEAVE" → "Paternity Leave"
        } catch (IllegalArgumentException e) {
            return rawName; // fallback to raw name if not found in enum
        }
    }

    public static String getMakerRole(Authentication authentication){
        return  authentication.getAuthorities().
                stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth->auth.replace("ROLE_", ""))
                .findFirst()
                .orElse("HR");
    }
}
