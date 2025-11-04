package com.paves.employee_leave_management.audit_new;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHelper implements ApplicationContextAware {
    private static ApplicationContext ctx;
    @Override public void setApplicationContext(ApplicationContext applicationContext) { ctx = applicationContext; }
    public static <T> T getBean(Class<T> cls) {
        return ctx == null ? null : ctx.getBean(cls);
    }
}