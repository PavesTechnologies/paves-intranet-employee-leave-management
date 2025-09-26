package com.paves.employee_leave_management.audit;

import java.lang.annotation.*;

@Target(ElementType.METHOD) // Can be applied to methods
@Retention(RetentionPolicy.RUNTIME) // Keep at runtime so AspectJ can read it
@Documented
public @interface Auditable {
}
