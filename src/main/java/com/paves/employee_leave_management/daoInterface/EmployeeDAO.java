package com.paves.employee_leave_management.daoInterface;

import com.paves.employee_leave_management.entities.Employee;

public interface EmployeeDAO {
    Employee saveEmployee(Employee employee);
    Employee updateEmployee(String employeeId,Employee employee);
}
