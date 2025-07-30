# PEMS - Employee Leave Management System API Documentation

## Overview
This document provides comprehensive API documentation for the PEMS (Paves Employee Management System) leave management application. The system manages employee leave requests, balances, and approvals with role-based access control.

## Base URL
```
http://localhost:8080
```

## Test Data
- **Male Employee**: `PAVEMPBFE4E`
- **Female Employee**: `PAVEMP3E18B`
- **Manager**: `PAVEMP69DA9`

## Common Response Format
Most endpoints return responses in the following format:
```json
{
  "success": boolean,
  "message": "string",
  "data": object
}
```

---

## 1. Employee Management Endpoints

### 1.1 Register Employee
**POST** `/api/employee/register`

**Description**: Register a new employee in the system.

**Request Body**:
```json
{
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "phone": "string",
  "hireDate": "YYYY-MM-DD",
  "salary": 50000.00,
  "jobTitle": "string",
  "gender": "string",
  "manager": {
    "employeeId": "PAVEMP69DA9"
  }
}
```

**Success Response (200)**:
```json
{
  "employeeId": "PAVEMPXXXXX",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@paves.com",
  "phone": "+1234567890",
  "hireDate": "2024-01-15",
  "salary": 50000.00,
  "jobTitle": "Software Engineer",
  "gender": "Male",
  "manager": {
    "employeeId": "PAVEMP69DA9"
  }
}
```

**Error Responses**:
- **400 Bad Request**: Invalid input data
- **409 Conflict**: Email already exists

### 1.2 Update Employee
**PUT** `/api/employee/update/{employeeId}`

**Description**: Update existing employee information.

**Path Parameters**:
- `employeeId` (string): Employee ID

**Request Body**: Same as register employee

**Success Response (200)**: Updated employee object

**Error Responses**:
- **404 Not Found**: Employee not found
- **400 Bad Request**: Invalid input data

---

## 2. Leave Type Management Endpoints

### 2.1 Add Leave Type
**POST** `/api/leave/add-leave-type`

**Description**: Create a new leave type in the system.

**Request Body**:
```json
{
  "leaveName": "Annual Leave",
  "description": "Yearly vacation leave",
  "maxDaysPerYear": 25.0,
  "maxConsecutiveDays": 15.0,
  "minDaysPerRequest": 0.5,
  "carryForwardAllowed": true,
  "maxCarryForwardDays": 5.0,
  "encashmentAllowed": true,
  "maxEncashmentDays": 10.0,
  "noticePeriodRestriction": false,
  "weekendsAndHolidaysAllowed": false
}
```

**Success Response (200)**:
```json
{
  "leaveTypeId": "LXXXXX",
  "leaveName": "Annual Leave",
  "description": "Yearly vacation leave",
  "maxDaysPerYear": 25.0,
  "maxConsecutiveDays": 15.0,
  "minDaysPerRequest": 0.5,
  "carryForwardAllowed": true,
  "maxCarryForwardDays": 5.0,
  "encashmentAllowed": true,
  "maxEncashmentDays": 10.0,
  "noticePeriodRestriction": false,
  "weekendsAndHolidaysAllowed": false
}
```

### 2.2 Get All Leave Types
**GET** `/api/leave/get-all-leave-types`

**Description**: Retrieve all available leave types.

**Success Response (200)**:
```json
[
  {
    "leaveTypeId": "LXXXXX",
    "leaveName": "Annual Leave",
    "description": "Yearly vacation leave",
    "maxDaysPerYear": 25.0,
    "maxConsecutiveDays": 15.0,
    "minDaysPerRequest": 0.5,
    "carryForwardAllowed": true,
    "maxCarryForwardDays": 5.0,
    "encashmentAllowed": true,
    "maxEncashmentDays": 10.0,
    "noticePeriodRestriction": false,
    "weekendsAndHolidaysAllowed": false
  }
]
```

**Error Response**:
- **204 No Content**: No leave types found

### 2.3 Update Leave Type
**PUT** `/api/leave/update-leave-type`

**Description**: Update an existing leave type.

**Request Body**: Same as add leave type with `leaveTypeId`

**Success Response (202)**: Updated leave type object

**Error Response**:
- **404 Not Found**: Leave type not found

---

## 3. Leave Request Management Endpoints

### 3.1 Apply for Leave
**POST** `/api/leave-requests/apply`

**Description**: Employee submits a new leave request.

**Request Body**:
```json
{
  "employeeId": "PAVEMPBFE4E",
  "leaveTypeId": "LXXXXX",
  "startDate": "2024-08-15",
  "endDate": "2024-08-17",
  "daysRequested": 3.0,
  "reason": "Family vacation",
  "driveLink": "https://drive.google.com/file/d/xxxxx"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave application submitted successfully",
  "data": {
    "leaveId": "LRXXXXX",
    "employee": {
      "employeeId": "PAVEMPBFE4E",
      "firstName": "John",
      "lastName": "Doe"
    },
    "leaveType": {
      "leaveTypeId": "LXXXXX",
      "leaveName": "Annual Leave"
    },
    "startDate": "2024-08-15",
    "endDate": "2024-08-17",
    "daysRequested": 3.0,
    "reason": "Family vacation",
    "driveLink": "https://drive.google.com/file/d/xxxxx",
    "status": "PENDING",
    "requestDate": "2024-07-30"
  }
}
```

**Error Responses**:
- **400 Bad Request**: Validation errors (insufficient balance, overlapping requests, etc.)
- **500 Internal Server Error**: Server processing error

### 3.2 Update Leave Request (Employee)
**PUT** `/api/leave-requests/employee/update`

**Description**: Employee updates their pending leave request.

**Request Body**:
```json
{
  "leaveId": "LRXXXXX",
  "employeeId": "PAVEMPBFE4E",
  "leaveTypeId": "LXXXXX",
  "startDate": "2024-08-16",
  "endDate": "2024-08-18",
  "daysRequested": 3.0,
  "reason": "Updated family vacation dates",
  "driveLink": "https://drive.google.com/file/d/xxxxx"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave request updated successfully",
  "data": {
    "isValid": true,
    "errors": [],
    "messages": ["Leave request updated successfully"],
    "employeeId": "PAVEMPBFE4E",
    "employeeName": "John Doe",
    "availableBalance": 22.0,
    "requestedDays": 3.0,
    "leaveId": "LRXXXXX"
  }
}
```

### 3.3 Cancel Leave Request
**PUT** `/api/leave-requests/{leaveId}/cancel`

**Description**: Employee cancels their leave request.

**Path Parameters**:
- `leaveId` (string): Leave request ID

**Query Parameters**:
- `employeeId` (string): Employee ID

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave request cancelled successfully",
  "data": {
    "leaveId": "LRXXXXX",
    "status": "CANCELLED",
    "cancelledDate": "2024-07-30"
  }
}
```

### 3.4 Get Employee Leave Requests
**GET** `/api/leave-requests/employee/{employeeId}`

**Description**: Retrieve all leave requests for a specific employee.

**Path Parameters**:
- `employeeId` (string): Employee ID

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave requests retrieved successfully",
  "data": [
    {
      "leaveId": "LRXXXXX",
      "leaveType": {
        "leaveTypeId": "LXXXXX",
        "leaveName": "Annual Leave"
      },
      "startDate": "2024-08-15",
      "endDate": "2024-08-17",
      "daysRequested": 3.0,
      "reason": "Family vacation",
      "status": "PENDING",
      "requestDate": "2024-07-30"
    }
  ]
}
```

### 3.5 Get Leave Request by ID
**GET** `/api/leave-requests/{leaveId}`

**Description**: Retrieve a specific leave request by ID.

**Path Parameters**:
- `leaveId` (string): Leave request ID

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave request retrieved successfully",
  "data": {
    "leaveId": "LRXXXXX",
    "employee": {
      "employeeId": "PAVEMPBFE4E",
      "firstName": "John",
      "lastName": "Doe"
    },
    "leaveType": {
      "leaveTypeId": "LXXXXX",
      "leaveName": "Annual Leave"
    },
    "startDate": "2024-08-15",
    "endDate": "2024-08-17",
    "daysRequested": 3.0,
    "reason": "Family vacation",
    "status": "PENDING",
    "requestDate": "2024-07-30"
  }
}
```

### 3.6 Get Leave History by Year
**GET** `/api/leave-requests/history/{employeeId}`

**Description**: Get employee's leave history for a specific year.

**Path Parameters**:
- `employeeId` (string): Employee ID

**Query Parameters**:
- `year` (integer): Year (e.g., 2024)

**Success Response (200)**:
```json
[
  {
    "leaveId": "LRXXXXX",
    "leaveType": {
      "leaveName": "Annual Leave"
    },
    "startDate": "2024-08-15",
    "endDate": "2024-08-17",
    "daysRequested": 3.0,
    "status": "APPROVED",
    "requestDate": "2024-07-30",
    "approvedDate": "2024-08-01"
  }
]
```

### 3.7 Validate Leave Request
**POST** `/api/leave-requests/validate`

**Description**: Validate a leave request without saving it.

**Request Body**: Same as apply leave request

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Validation completed",
  "data": {
    "isValid": true,
    "errors": [],
    "messages": ["Validation successful"],
    "employeeId": "PAVEMPBFE4E",
    "employeeName": "John Doe",
    "availableBalance": 25.0,
    "requestedDays": 3.0
  }
}
```

### 3.8 Check Overlapping Requests
**POST** `/api/leave-requests/check-overlap`

**Description**: Check for overlapping leave requests for an employee.

**Request Body**:
```json
{
  "employeeId": "PAVEMPBFE4E",
  "startDate": "2024-08-15",
  "endDate": "2024-08-17"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "No overlapping requests",
  "data": false
}
```

### 3.9 Get Employee Leave Balance
**GET** `/api/leave-requests/balance/{employeeId}/{leaveTypeId}`

**Description**: Get employee's leave balance for a specific leave type.

**Path Parameters**:
- `employeeId` (string): Employee ID
- `leaveTypeId` (string): Leave type ID

**Query Parameters**:
- `year` (integer, optional): Year (defaults to current year)

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Balance retrieved successfully",
  "data": {
    "balanceId": "BALXXXXX",
    "employeeId": "PAVEMPBFE4E",
    "employeeName": "John Doe",
    "leaveTypeId": "LXXXXX",
    "leaveTypeName": "Annual Leave",
    "totalLeaves": 25.0,
    "accruedLeaves": 25.0,
    "usedLeaves": 3.0,
    "remainingLeaves": 22.0,
    "carriedForward": 0.0,
    "year": 2024
  }
}
```

---

## 4. Manager Operations

### 4.1 Approve Leave Request
**PUT** `/api/leave-requests/approve`

**Description**: Manager approves a leave request.

**Request Body**:
```json
{
  "managerId": "PAVEMP69DA9",
  "leaveId": "LRXXXXX",
  "comment": "Approved for family vacation"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave request approved successfully",
  "data": {
    "leaveId": "LRXXXXX",
    "status": "APPROVED",
    "approvedBy": {
      "employeeId": "PAVEMP69DA9",
      "firstName": "Manager",
      "lastName": "Name"
    },
    "approvedDate": "2024-07-30",
    "managerComment": "Approved for family vacation"
  }
}
```

### 4.2 Update Leave Request (Manager)
**PUT** `/api/leave-requests/update`

**Description**: Manager updates a leave request.

**Request Body**:
```json
{
  "managerId": "PAVEMP69DA9",
  "leaveId": "LRXXXXX",
  "leaveTypeId": "LXXXXX",
  "startDate": "2024-08-16",
  "endDate": "2024-08-18",
  "comment": "Adjusted dates as requested",
  "daysRequested": 3.0
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave request updated successfully",
  "data": {
    "leaveId": "LRXXXXX",
    "startDate": "2024-08-16",
    "endDate": "2024-08-18",
    "daysRequested": 3.0,
    "managerComment": "Adjusted dates as requested",
    "lastModifiedBy": {
      "employeeId": "PAVEMP69DA9"
    },
    "lastModifiedDate": "2024-07-30"
  }
}
```

### 4.3 Get Leave History for Manager
**POST** `/api/leave-requests/manager/history`

**Description**: Get filtered leave history for manager's team.

**Request Body**:
```json
{
  "managerId": "PAVEMP69DA9",
  "status": "PENDING",
  "fromDate": "2024-01-01",
  "toDate": "2024-12-31",
  "employeeId": "PAVEMPBFE4E",
  "leaveTypeId": "LXXXXX"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Leave history retrieved successfully",
  "data": [
    {
      "leaveId": "LRXXXXX",
      "employee": {
        "employeeId": "PAVEMPBFE4E",
        "firstName": "John",
        "lastName": "Doe"
      },
      "leaveType": {
        "leaveName": "Annual Leave"
      },
      "startDate": "2024-08-15",
      "endDate": "2024-08-17",
      "daysRequested": 3.0,
      "status": "PENDING",
      "requestDate": "2024-07-30"
    }
  ]
}
```

---

## 5. Leave Balance Management

### 5.1 Get All Leave Balances
**GET** `/api/leave-balance`

**Description**: Retrieve all leave balances in the system.

**Success Response (200)**:
```json
[
  {
    "balanceId": "BALXXXXX",
    "employee": {
      "employeeId": "PAVEMPBFE4E",
      "firstName": "John",
      "lastName": "Doe"
    },
    "leaveType": {
      "leaveTypeId": "LXXXXX",
      "leaveName": "Annual Leave"
    },
    "totalLeaves": 25.0,
    "accruedLeaves": 25.0,
    "usedLeaves": 3.0,
    "remainingLeaves": 22.0,
    "carriedForward": 0.0,
    "year": 2024
  }
]
```

### 5.2 Get Leave Balance by ID
**GET** `/api/leave-balance/{balanceID}`

**Description**: Retrieve a specific leave balance by ID.

**Path Parameters**:
- `balanceID` (string): Balance ID

**Success Response (200)**:
```json
{
  "balanceId": "BALXXXXX",
  "employee": {
    "employeeId": "PAVEMPBFE4E",
    "firstName": "John",
    "lastName": "Doe"
  },
  "leaveType": {
    "leaveTypeId": "LXXXXX",
    "leaveName": "Annual Leave"
  },
  "totalLeaves": 25.0,
  "accruedLeaves": 25.0,
  "usedLeaves": 3.0,
  "remainingLeaves": 22.0,
  "carriedForward": 0.0,
  "year": 2024
}
```

### 5.3 Get Leave Balances by Employee
**GET** `/api/leave-balance/employee/{employeeId}`

**Description**: Retrieve all leave balances for a specific employee.

**Path Parameters**:
- `employeeId` (string): Employee ID

**Success Response (200)**:
```json
[
  {
    "balanceId": "BALXXXXX",
    "leaveType": {
      "leaveTypeId": "LXXXXX",
      "leaveName": "Annual Leave"
    },
    "totalLeaves": 25.0,
    "accruedLeaves": 25.0,
    "usedLeaves": 3.0,
    "remainingLeaves": 22.0,
    "carriedForward": 0.0,
    "year": 2024
  }
]
```

### 5.4 Get Leave Balances by Leave Type
**GET** `/api/leave-balance/type/{leaveTypeId}`

**Description**: Retrieve all leave balances for a specific leave type.

**Path Parameters**:
- `leaveTypeId` (string): Leave type ID

**Success Response (200)**:
```json
[
  {
    "balanceId": "BALXXXXX",
    "employee": {
      "employeeId": "PAVEMPBFE4E",
      "firstName": "John",
      "lastName": "Doe"
    },
    "totalLeaves": 25.0,
    "accruedLeaves": 25.0,
    "usedLeaves": 3.0,
    "remainingLeaves": 22.0,
    "carriedForward": 0.0,
    "year": 2024
  }
]
```

### 5.5 Generate Leave Balance for New Employee
**POST** `/api/leave-balance/generate/{employeeId}`

**Description**: Generate initial leave balances for a new employee.

**Path Parameters**:
- `employeeId` (string): Employee ID

**Success Response (200)**:
```json
"Leave balance generated successfully for employee: PAVEMPBFE4E"
```

### 5.6 Carry Forward Leave Balances
**POST** `/api/leave-balance/carryforward`

**Description**: Process year-end carry forward for all employees.

**Success Response (200)**:
```json
"Carry forward process completed successfully."
```

### 5.7 Monthly Leave Balance Process
**POST** `/api/leave-balance/monthly-process`

**Description**: Run monthly leave accrual process.

**Success Response (200)**:
```json
"Monthly process triggered successfully."
```

### 5.8 Update Leave Balance After Approval
**POST** `/api/leave-balance/update-leave-balance`

**Description**: Update leave balance after leave approval.

**Query Parameters**:
- `employeeId` (string): Employee ID
- `leaveTypeId` (string): Leave type ID
- `approvedDays` (double): Number of approved days

**Success Response (200)**:
```json
"Leave approved and balance updated successfully."
```

---

## 6. Compensatory Off (Comp-off) Management

### 6.1 Request Comp-off
**POST** `/api/compoff/request`

**Description**: Employee requests compensatory off for overtime work.

**Request Body**:
```json
{
  "employeeId": "PAVEMPBFE4E",
  "managerId": "PAVEMP69DA9",
  "workedDate": "2024-07-28",
  "startDate": "2024-08-05",
  "endDate": "2024-08-05",
  "days": 1.0,
  "halfDays": "FULL_DAY",
  "note": "Worked on weekend for project deadline",
  "file": "evidence_file_url"
}
```

**Success Response (200)**:
```json
"Compoff requested successfully."
```

### 6.2 Update Comp-off Status
**PUT** `/api/compoff/update-status`

**Description**: Manager updates comp-off request status.

**Request Body**:
```json
{
  "compoffId": 1,
  "status": "APPROVED"
}
```

**Success Response (200)**:
```json
"Compoff status updated successfully."
```

### 6.3 Get Comp-off by Employee
**GET** `/api/compoff/employee/{employeeId}`

**Description**: Retrieve all comp-off requests for an employee.

**Path Parameters**:
- `employeeId` (string): Employee ID

**Success Response (200)**:
```json
[
  {
    "idleaveCompoff": 1,
    "employeeId": "PAVEMPBFE4E",
    "managerId": "PAVEMP69DA9",
    "workedDate": "2024-07-28",
    "startDate": "2024-08-05",
    "endDate": "2024-08-05",
    "days": 1.0,
    "halfDays": "FULL_DAY",
    "note": "Worked on weekend for project deadline",
    "file": "evidence_file_url",
    "status": "PENDING",
    "actionDate": null,
    "expiryDate": "2024-10-28"
  }
]
```

### 6.4 Get Comp-off by Manager and Status
**GET** `/api/compoff/manager/{managerId}/status/{status}`

**Description**: Retrieve comp-off requests by manager and status.

**Path Parameters**:
- `managerId` (string): Manager ID
- `status` (string): Status (PENDING, APPROVED, REJECTED, EXPIRED)

**Success Response (200)**:
```json
[
  {
    "idleaveCompoff": 1,
    "employeeId": "PAVEMPBFE4E",
    "managerId": "PAVEMP69DA9",
    "workedDate": "2024-07-28",
    "startDate": "2024-08-05",
    "endDate": "2024-08-05",
    "days": 1.0,
    "halfDays": "FULL_DAY",
    "note": "Worked on weekend for project deadline",
    "file": "evidence_file_url",
    "status": "PENDING",
    "actionDate": null,
    "expiryDate": "2024-10-28"
  }
]
```

---

## Error Codes and Messages

### Common HTTP Status Codes
- **200 OK**: Request successful
- **201 Created**: Resource created successfully
- **202 Accepted**: Request accepted for processing
- **204 No Content**: No data found
- **400 Bad Request**: Invalid request data
- **401 Unauthorized**: Authentication required
- **403 Forbidden**: Access denied
- **404 Not Found**: Resource not found
- **409 Conflict**: Resource already exists
- **500 Internal Server Error**: Server error

### Common Error Messages
- "Employee not found"
- "Leave type not found"
- "Leave request not found"
- "Insufficient leave balance"
- "Overlapping leave requests found"
- "Invalid date range"
- "Leave request cannot be modified in current status"
- "Manager authorization required"
- "Validation failed"

---

## Data Models

### Employee
```json
{
  "employeeId": "string",
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "phone": "string",
  "hireDate": "YYYY-MM-DD",
  "salary": "decimal",
  "jobTitle": "string",
  "gender": "string",
  "manager": {
    "employeeId": "string"
  }
}
```

### LeaveType
```json
{
  "leaveTypeId": "string",
  "leaveName": "string",
  "description": "string",
  "maxDaysPerYear": "decimal",
  "maxConsecutiveDays": "decimal",
  "minDaysPerRequest": "decimal",
  "carryForwardAllowed": "boolean",
  "maxCarryForwardDays": "decimal",
  "encashmentAllowed": "boolean",
  "maxEncashmentDays": "decimal",
  "noticePeriodRestriction": "boolean",
  "weekendsAndHolidaysAllowed": "boolean"
}
```

### LeaveRequest
```json
{
  "leaveId": "string",
  "employee": "Employee",
  "leaveType": "LeaveType",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "daysRequested": "decimal",
  "reason": "string",
  "driveLink": "string",
  "status": "PENDING|APPROVED|REJECTED|CANCELLED",
  "requestDate": "YYYY-MM-DD",
  "approvedBy": "Employee",
  "approvedDate": "YYYY-MM-DD",
  "managerComment": "string"
}
```

### LeaveBalance
```json
{
  "balanceId": "string",
  "employee": "Employee",
  "leaveType": "LeaveType",
  "totalLeaves": "decimal",
  "accruedLeaves": "decimal",
  "usedLeaves": "decimal",
  "remainingLeaves": "decimal",
  "carriedForward": "decimal",
  "expiredLeaves": "decimal",
  "encashedLeaves": "integer",
  "year": "integer",
  "lastAccrualDate": "YYYY-MM-DD"
}
```

### LeaveCompoff
```json
{
  "idleaveCompoff": "integer",
  "employeeId": "string",
  "managerId": "string",
  "workedDate": "YYYY-MM-DD",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "days": "decimal",
  "halfDays": "string",
  "note": "string",
  "file": "string",
  "status": "PENDING|APPROVED|REJECTED|EXPIRED",
  "actionDate": "YYYY-MM-DD",
  "expiryDate": "YYYY-MM-DD"
}
```

---

## Notes
1. All dates should be in ISO format (YYYY-MM-DD)
2. Employee IDs are auto-generated with prefix "PAVEMP"
3. Leave IDs are auto-generated with prefix "LR"
4. Leave Type IDs are auto-generated with prefix "L"
5. Balance IDs are auto-generated with prefix "BAL"
6. All decimal values support up to 2 decimal places
7. Cross-origin requests are enabled for frontend integration
8. Validation is performed on all input data
9. Manager authorization is required for approval/rejection operations
10. Leave balance is automatically updated upon leave approval
