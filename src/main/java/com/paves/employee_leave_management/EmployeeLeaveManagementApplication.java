package com.paves.employee_leave_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.paves.employee_leave_management")
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.paves.employee_leave_management")
@EnableTransactionManagement
@EntityScan(basePackages = "com.paves.employee_leave_management")
@EnableAsync
public class EmployeeLeaveManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeLeaveManagementApplication.class, args);
    }
}
