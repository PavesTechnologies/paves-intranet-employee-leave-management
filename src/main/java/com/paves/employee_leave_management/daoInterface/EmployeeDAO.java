package com.paves.employee_leave_management.daoInterface;

import com.paves.employee_leave_management.entities.Employee;
import org.hibernate.Hibernate;

import java.util.List;
import java.util.Optional;

public interface EmployeeDAO {
    Employee saveEmployee(Employee employee);
    Employee updateEmployee(String employeeId,Employee employee);

    Optional<Employee> findByEmployeeId(String employeeId);

   List<Employee> findAll();
}
