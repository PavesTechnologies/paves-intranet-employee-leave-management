# PEMS API Test Cases

## Test Data Setup
- **Male Employee**: `PAVEMPBFE4E`
- **Female Employee**: `PAVEMP3E18B` 
- **Manager**: `PAVEMP69DA9`

## 1. Employee Management Tests

### 1.1 Register Employee Tests

**Test Case 1.1.1: Valid Employee Registration**
```
POST /api/employee/register
Body: {
  "firstName": "John",
  "lastName": "Doe", 
  "email": "john.doe@test.com",
  "phone": "+1234567890",
  "hireDate": "2024-01-15",
  "salary": 50000.00,
  "jobTitle": "Software Engineer",
  "gender": "Male",
  "manager": {"employeeId": "PAVEMP69DA9"}
}
Expected: 200 OK with employee object
```

**Test Case 1.1.2: Duplicate Email Registration**
```
POST /api/employee/register
Body: Same email as existing employee
Expected: 409 Conflict
```

**Test Case 1.1.3: Invalid Email Format**
```
POST /api/employee/register
Body: {"email": "invalid-email"}
Expected: 400 Bad Request
```

**Test Case 1.1.4: Missing Required Fields**
```
POST /api/employee/register
Body: {"firstName": "John"} // Missing other required fields
Expected: 400 Bad Request
```

**Test Case 1.1.5: Invalid Manager ID**
```
POST /api/employee/register
Body: {"manager": {"employeeId": "INVALID"}}
Expected: 400 Bad Request
```

### 1.2 Update Employee Tests

**Test Case 1.2.1: Valid Employee Update**
```
PUT /api/employee/update/PAVEMPBFE4E
Body: {"firstName": "Updated John", "lastName": "Doe"}
Expected: 200 OK with updated employee
```

**Test Case 1.2.2: Update Non-existent Employee**
```
PUT /api/employee/update/INVALID
Expected: 404 Not Found
```

## 2. Leave Type Management Tests

### 2.1 Add Leave Type Tests

**Test Case 2.1.1: Valid Leave Type Creation**
```
POST /api/leave/add-leave-type
Body: {
  "leaveName": "Annual Leave",
  "description": "Yearly vacation",
  "maxDaysPerYear": 25.0,
  "maxConsecutiveDays": 15.0,
  "minDaysPerRequest": 0.5,
  "carryForwardAllowed": true,
  "maxCarryForwardDays": 5.0
}
Expected: 200 OK with leave type object
```

**Test Case 2.1.2: Duplicate Leave Type Name**
```
POST /api/leave/add-leave-type
Body: Same leaveName as existing
Expected: 200 OK (overwrites existing)
```

**Test Case 2.1.3: Invalid Numeric Values**
```
POST /api/leave/add-leave-type
Body: {"maxDaysPerYear": -5}
Expected: 400 Bad Request
```

### 2.2 Get All Leave Types Tests

**Test Case 2.2.1: Get All Leave Types - Success**
```
GET /api/leave/get-all-leave-types
Expected: 200 OK with array of leave types
```

**Test Case 2.2.2: Get All Leave Types - Empty**
```
GET /api/leave/get-all-leave-types
Expected: 204 No Content when no leave types exist
```

## 3. Leave Request Management Tests

### 3.1 Apply Leave Tests

**Test Case 3.1.1: Valid Leave Application**
```
POST /api/leave-requests/apply
Body: {
  "employeeId": "PAVEMPBFE4E",
  "leaveTypeId": "LXXXXX",
  "startDate": "2024-08-15",
  "endDate": "2024-08-17",
  "daysRequested": 3.0,
  "reason": "Family vacation"
}
Expected: 200 OK with leave request object
```

**Test Case 3.1.2: Insufficient Leave Balance**
```
POST /api/leave-requests/apply
Body: {"daysRequested": 100.0} // More than available balance
Expected: 400 Bad Request with validation error
```

**Test Case 3.1.3: Invalid Date Range**
```
POST /api/leave-requests/apply
Body: {
  "startDate": "2024-08-17",
  "endDate": "2024-08-15" // End before start
}
Expected: 400 Bad Request
```

**Test Case 3.1.4: Weekend/Holiday Restriction**
```
POST /api/leave-requests/apply
Body: Weekend dates for leave type that doesn't allow weekends
Expected: 400 Bad Request
```

**Test Case 3.1.5: Overlapping Leave Request**
```
POST /api/leave-requests/apply
Body: Dates overlapping with existing pending/approved leave
Expected: 400 Bad Request
```

**Test Case 3.1.6: Past Date Application**
```
POST /api/leave-requests/apply
Body: {"startDate": "2024-01-01"} // Past date
Expected: 400 Bad Request
```

**Test Case 3.1.7: Exceeds Maximum Consecutive Days**
```
POST /api/leave-requests/apply
Body: {"daysRequested": 20.0} // Exceeds maxConsecutiveDays
Expected: 400 Bad Request
```

**Test Case 3.1.8: Below Minimum Days**
```
POST /api/leave-requests/apply
Body: {"daysRequested": 0.25} // Below minDaysPerRequest
Expected: 400 Bad Request
```

### 3.2 Update Leave Request Tests

**Test Case 3.2.1: Valid Employee Update**
```
PUT /api/leave-requests/employee/update
Body: {
  "leaveId": "LRXXXXX",
  "employeeId": "PAVEMPBFE4E",
  "startDate": "2024-08-16",
  "endDate": "2024-08-18",
  "reason": "Updated reason"
}
Expected: 200 OK with validation result
```

**Test Case 3.2.2: Update Approved Leave**
```
PUT /api/leave-requests/employee/update
Body: Update request with status APPROVED
Expected: 400 Bad Request (cannot modify approved leave)
```

**Test Case 3.2.3: Update Non-existent Leave**
```
PUT /api/leave-requests/employee/update
Body: {"leaveId": "INVALID"}
Expected: 404 Not Found
```

### 3.3 Cancel Leave Tests

**Test Case 3.3.1: Valid Cancellation**
```
PUT /api/leave-requests/LRXXXXX/cancel?employeeId=PAVEMPBFE4E
Expected: 200 OK with cancelled leave request
```

**Test Case 3.3.2: Cancel Non-existent Leave**
```
PUT /api/leave-requests/INVALID/cancel?employeeId=PAVEMPBFE4E
Expected: 404 Not Found
```

**Test Case 3.3.3: Cancel Another Employee's Leave**
```
PUT /api/leave-requests/LRXXXXX/cancel?employeeId=WRONGEMP
Expected: 403 Forbidden
```

### 3.4 Get Leave Requests Tests

**Test Case 3.4.1: Get Employee Leave Requests**
```
GET /api/leave-requests/employee/PAVEMPBFE4E
Expected: 200 OK with array of leave requests
```

**Test Case 3.4.2: Get Non-existent Employee Requests**
```
GET /api/leave-requests/employee/INVALID
Expected: 200 OK with empty array
```

**Test Case 3.4.3: Get Leave Request by ID**
```
GET /api/leave-requests/LRXXXXX
Expected: 200 OK with leave request object
```

**Test Case 3.4.4: Get Non-existent Leave Request**
```
GET /api/leave-requests/INVALID
Expected: 404 Not Found
```

### 3.5 Leave History Tests

**Test Case 3.5.1: Get Leave History by Year**
```
GET /api/leave-requests/history/PAVEMPBFE4E?year=2024
Expected: 200 OK with array of leave requests for 2024
```

**Test Case 3.5.2: Get Leave History - Invalid Year**
```
GET /api/leave-requests/history/PAVEMPBFE4E?year=abc
Expected: 400 Bad Request
```

### 3.6 Validation Tests

**Test Case 3.6.1: Validate Valid Request**
```
POST /api/leave-requests/validate
Body: Valid leave request data
Expected: 200 OK with validation result (isValid: true)
```

**Test Case 3.6.2: Validate Invalid Request**
```
POST /api/leave-requests/validate
Body: Invalid leave request data
Expected: 200 OK with validation result (isValid: false, errors array)
```

### 3.7 Overlap Check Tests

**Test Case 3.7.1: No Overlapping Requests**
```
POST /api/leave-requests/check-overlap
Body: {
  "employeeId": "PAVEMPBFE4E",
  "startDate": "2024-09-15",
  "endDate": "2024-09-17"
}
Expected: 200 OK with data: false
```

**Test Case 3.7.2: Overlapping Requests Found**
```
POST /api/leave-requests/check-overlap
Body: Dates overlapping with existing request
Expected: 200 OK with data: true
```

### 3.8 Leave Balance Tests

**Test Case 3.8.1: Get Valid Leave Balance**
```
GET /api/leave-requests/balance/PAVEMPBFE4E/LXXXXX
Expected: 200 OK with leave balance object
```

**Test Case 3.8.2: Get Balance for Invalid Employee**
```
GET /api/leave-requests/balance/INVALID/LXXXXX
Expected: 404 Not Found
```

**Test Case 3.8.3: Get Balance with Year Parameter**
```
GET /api/leave-requests/balance/PAVEMPBFE4E/LXXXXX?year=2023
Expected: 200 OK with balance for 2023
```

## 4. Manager Operations Tests

### 4.1 Approve Leave Tests

**Test Case 4.1.1: Valid Approval**
```
PUT /api/leave-requests/approve
Body: {
  "managerId": "PAVEMP69DA9",
  "leaveId": "LRXXXXX",
  "comment": "Approved"
}
Expected: 200 OK with approved leave request
```

**Test Case 4.1.2: Approve Already Approved Leave**
```
PUT /api/leave-requests/approve
Body: Leave request already approved
Expected: 400 Bad Request
```

**Test Case 4.1.3: Unauthorized Manager Approval**
```
PUT /api/leave-requests/approve
Body: {"managerId": "WRONGMANAGER"}
Expected: 403 Forbidden
```

### 4.2 Manager Update Tests

**Test Case 4.2.1: Valid Manager Update**
```
PUT /api/leave-requests/update
Body: {
  "managerId": "PAVEMP69DA9",
  "leaveId": "LRXXXXX",
  "startDate": "2024-08-16",
  "comment": "Adjusted dates"
}
Expected: 200 OK with updated leave request
```

### 4.3 Manager History Tests

**Test Case 4.3.1: Get Manager History with Filters**
```
POST /api/leave-requests/manager/history
Body: {
  "managerId": "PAVEMP69DA9",
  "status": "PENDING",
  "fromDate": "2024-01-01",
  "toDate": "2024-12-31"
}
Expected: 200 OK with filtered leave requests
```

## 5. Leave Balance Management Tests

### 5.1 Get Balance Tests

**Test Case 5.1.1: Get All Leave Balances**
```
GET /api/leave-balance
Expected: 200 OK with array of all balances
```

**Test Case 5.1.2: Get Balance by ID**
```
GET /api/leave-balance/BALXXXXX
Expected: 200 OK with balance object
```

**Test Case 5.1.3: Get Balance by Employee**
```
GET /api/leave-balance/employee/PAVEMPBFE4E
Expected: 200 OK with employee's balances
```

**Test Case 5.1.4: Get Balance by Leave Type**
```
GET /api/leave-balance/type/LXXXXX
Expected: 200 OK with balances for leave type
```

### 5.2 Generate Balance Tests

**Test Case 5.2.1: Generate Balance for New Employee**
```
POST /api/leave-balance/generate/PAVEMPBFE4E
Expected: 200 OK with success message
```

**Test Case 5.2.2: Generate Balance for Invalid Employee**
```
POST /api/leave-balance/generate/INVALID
Expected: 404 Not Found
```

### 5.3 Process Tests

**Test Case 5.3.1: Carry Forward Process**
```
POST /api/leave-balance/carryforward
Expected: 200 OK with success message
```

**Test Case 5.3.2: Monthly Process**
```
POST /api/leave-balance/monthly-process
Expected: 200 OK with success message
```

### 5.4 Update Balance Tests

**Test Case 5.4.1: Update Balance After Approval**
```
POST /api/leave-balance/update-leave-balance?employeeId=PAVEMPBFE4E&leaveTypeId=LXXXXX&approvedDays=3.0
Expected: 200 OK with success message
```

## 6. Comp-off Management Tests

### 6.1 Request Comp-off Tests

**Test Case 6.1.1: Valid Comp-off Request**
```
POST /api/compoff/request
Body: {
  "employeeId": "PAVEMPBFE4E",
  "managerId": "PAVEMP69DA9",
  "workedDate": "2024-07-28",
  "startDate": "2024-08-05",
  "endDate": "2024-08-05",
  "days": 1.0,
  "note": "Weekend work"
}
Expected: 200 OK with success message
```

**Test Case 6.1.2: Invalid Worked Date (Future)**
```
POST /api/compoff/request
Body: {"workedDate": "2025-01-01"}
Expected: 400 Bad Request
```

**Test Case 6.1.3: Invalid Comp-off Date (Past)**
```
POST /api/compoff/request
Body: {"startDate": "2024-01-01"}
Expected: 400 Bad Request
```

### 6.2 Update Comp-off Status Tests

**Test Case 6.2.1: Valid Status Update**
```
PUT /api/compoff/update-status
Body: {
  "compoffId": 1,
  "status": "APPROVED"
}
Expected: 200 OK with success message
```

**Test Case 6.2.2: Invalid Comp-off ID**
```
PUT /api/compoff/update-status
Body: {"compoffId": 999}
Expected: 404 Not Found
```

**Test Case 6.2.3: Invalid Status**
```
PUT /api/compoff/update-status
Body: {"status": "INVALID_STATUS"}
Expected: 400 Bad Request
```

### 6.3 Get Comp-off Tests

**Test Case 6.3.1: Get by Employee**
```
GET /api/compoff/employee/PAVEMPBFE4E
Expected: 200 OK with array of comp-off requests
```

**Test Case 6.3.2: Get by Manager and Status**
```
GET /api/compoff/manager/PAVEMP69DA9/status/PENDING
Expected: 200 OK with filtered comp-off requests
```

**Test Case 6.3.3: Invalid Status in URL**
```
GET /api/compoff/manager/PAVEMP69DA9/status/INVALID
Expected: 400 Bad Request
```

## 7. Edge Cases and Boundary Tests

### 7.1 Date Boundary Tests

**Test Case 7.1.1: Year Boundary Leave Request**
```
POST /api/leave-requests/apply
Body: {
  "startDate": "2024-12-31",
  "endDate": "2025-01-02"
}
Expected: Test year-end boundary handling
```

**Test Case 7.1.2: Leap Year Handling**
```
POST /api/leave-requests/apply
Body: {"startDate": "2024-02-29"}
Expected: 200 OK (2024 is leap year)
```

### 7.2 Decimal Precision Tests

**Test Case 7.2.1: Half Day Leave**
```
POST /api/leave-requests/apply
Body: {"daysRequested": 0.5}
Expected: 200 OK
```

**Test Case 7.2.2: Quarter Day Leave**
```
POST /api/leave-requests/apply
Body: {"daysRequested": 0.25}
Expected: Based on minDaysPerRequest setting
```

### 7.3 Concurrent Request Tests

**Test Case 7.3.1: Simultaneous Leave Applications**
```
Multiple concurrent POST /api/leave-requests/apply
Body: Same employee, overlapping dates
Expected: Only one should succeed
```

### 7.4 Large Data Tests

**Test Case 7.4.1: Large Reason Text**
```
POST /api/leave-requests/apply
Body: {"reason": "Very long reason text..."}
Expected: Test text field limits
```

### 7.5 Special Character Tests

**Test Case 7.5.1: Special Characters in Names**
```
POST /api/employee/register
Body: {"firstName": "José", "lastName": "O'Connor"}
Expected: 200 OK
```

### 7.6 SQL Injection Tests

**Test Case 7.6.1: SQL Injection in Employee ID**
```
GET /api/leave-requests/employee/'; DROP TABLE employee; --
Expected: 400 Bad Request (no SQL injection)
```

### 7.7 Cross-Site Scripting Tests

**Test Case 7.7.1: XSS in Reason Field**
```
POST /api/leave-requests/apply
Body: {"reason": "<script>alert('xss')</script>"}
Expected: Text should be escaped/sanitized
```

## 8. Performance Tests

### 8.1 Load Tests

**Test Case 8.1.1: Multiple Employee Requests**
```
100 concurrent GET /api/leave-requests/employee/{employeeId}
Expected: All requests complete within acceptable time
```

**Test Case 8.1.2: Bulk Leave Applications**
```
50 concurrent POST /api/leave-requests/apply
Expected: System handles load without errors
```

### 8.2 Memory Tests

**Test Case 8.2.1: Large Dataset Retrieval**
```
GET /api/leave-balance (with 1000+ records)
Expected: Memory usage within limits
```

## 9. Security Tests

### 9.1 Authorization Tests

**Test Case 9.1.1: Access Another Employee's Data**
```
GET /api/leave-requests/employee/OTHER_EMPLOYEE
Expected: 403 Forbidden (if not manager)
```

**Test Case 9.1.2: Manager Access to Subordinate Data**
```
GET /api/leave-requests/employee/SUBORDINATE_ID
Headers: Manager authentication
Expected: 200 OK
```

### 9.2 Input Validation Tests

**Test Case 9.2.1: Invalid JSON Format**
```
POST /api/leave-requests/apply
Body: Invalid JSON
Expected: 400 Bad Request
```

**Test Case 9.2.2: Missing Content-Type Header**
```
POST /api/leave-requests/apply
Headers: No Content-Type
Expected: 400 Bad Request
```

## 10. Integration Tests

### 10.1 End-to-End Workflow Tests

**Test Case 10.1.1: Complete Leave Workflow**
```
1. POST /api/leave-requests/apply (Employee applies)
2. GET /api/leave-requests/manager/history (Manager views)
3. PUT /api/leave-requests/approve (Manager approves)
4. GET /api/leave-balance/employee/{id} (Check updated balance)
Expected: Complete workflow success
```

**Test Case 10.1.2: Leave Rejection Workflow**
```
1. POST /api/leave-requests/apply
2. PUT /api/leave-requests/reject
3. GET /api/leave-requests/employee/{id} (Check status)
Expected: Status shows REJECTED
```

### 10.2 Data Consistency Tests

**Test Case 10.2.1: Balance Consistency After Approval**
```
1. Check initial balance
2. Apply and approve leave
3. Verify balance reduction matches approved days
Expected: Balance correctly updated
```

**Test Case 10.2.2: Carry Forward Consistency**
```
1. Year-end carry forward process
2. Verify balances in new year
3. Check carry forward limits respected
Expected: Correct carry forward amounts
```

## Test Execution Notes

1. **Setup**: Ensure test database with sample data
2. **Cleanup**: Reset data between test suites
3. **Environment**: Use dedicated test environment
4. **Monitoring**: Log all API calls and responses
5. **Validation**: Verify database state after operations
6. **Performance**: Monitor response times and resource usage
7. **Security**: Test with different user roles and permissions
8. **Error Handling**: Verify proper error messages and codes
9. **Documentation**: Keep test results and coverage reports
10. **Automation**: Implement automated test execution
