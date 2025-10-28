package com.paves.employee_leave_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
public class  EmployeeLeaveManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmployeeLeaveManagementApplication.class, args);
	}
}
