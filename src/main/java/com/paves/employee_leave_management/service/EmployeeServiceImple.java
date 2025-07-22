package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.EmployeeDAO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.globalExceptionHandler.EmployeeExceptionHandler;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import org.hibernate.PropertyValueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class EmployeeServiceImple implements EmployeeServiceInterface {

    @Autowired
    EmployeeDAO employeeDAO;


    @Override
    public ResponseEntity<Employee> saveEmployee(Employee employee) {
        Employee emp = employeeDAO.saveEmployee(employee);
        if(emp != null) {
            return new ResponseEntity<Employee>(emp, HttpStatus.ACCEPTED);
        } else {
            throw new EmployeeExceptionHandler("Unable to save employee");
        }
    }

    @Override
    public ResponseEntity<Employee> updateEmployee(String employeeId, Employee employee) {

            Employee emp = employeeDAO.updateEmployee(employeeId, employee);
            if (emp != null) {
                return new ResponseEntity<Employee>(emp, HttpStatus.ACCEPTED);
            } else {
                throw new EmployeeExceptionHandler("Employee not found with id: " + employeeId);
            }

    }
}
