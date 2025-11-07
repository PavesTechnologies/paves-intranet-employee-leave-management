# Leave Request Management System - API Documentation

## Overview

This document provides a comprehensive list of all API endpoints in the Employee Leave Management System. All endpoints
are consolidated under the `LeaveRequestController` with a unified base path.

**Base URL:** `/api/leave-requests`

---

## 📋 Table of Contents

1. [Employee Operations](#employee-operations)
2. [Validation Operations](#validation-operations)
3. [Manager Operations](#manager-operations)
4. [Response Format](#response-format)
5. [Error Handling](#error-handling)

---

## 🧑‍💼 Employee Operations

### 1. Apply for Leave

- **Endpoint:** `POST /api/leave-requests/apply`
- **Description:** Submit a new leave request
- **Request Body:** `LeaveRequestValidationDTO`
- **Response:** `ApiResponse<LeaveRequest>`
- **Functionality:**
    - Validates the leave request against all business rules
    - Checks leave balance, overlapping requests, advance notice requirements
    - Validates leave type specific rules (ML, PL, etc.)
    - Saves the request with PENDING status if validation passes
- **Authorization:** Employee
- **Status Codes:**
    - `200 OK` - Request submitted successfully
    - `400 Bad Request` - Validation failed
    - `500 Internal Server Error` - System error

### 2. Update Leave Request

- **Endpoint:** `PUT /api/leave-requests/employee/update`
- **Description:** Update an existing leave request (only if pending)
- **Request Body:** `LeaveRequest`
- **Response:** `ApiResponse<ValidationResultDTO>`
- **Functionality:**
    - Allows employees to modify their pending leave requests
    - Re-validates the updated request
    - Prevents updates to approved/rejected requests
- **Authorization:** Employee (own requests only)
- **Status Codes:**
    - `200 OK` - Request updated successfully
    - `400 Bad Request` - Update not allowed or validation failed
    - `500 Internal Server Error` - System error

### 3. Get Employee Leave Requests

- **Endpoint:** `GET /api/leave-requests/employee/{employeeId}`
- **Description:** Retrieve all leave requests for a specific employee
- **Path Parameters:** `employeeId` - Employee ID
- **Response:** `ApiResponse<List<LeaveRequest>>`
- **Functionality:**
    - Returns complete leave history for the employee
    - Includes all statuses (PENDING, APPROVED, REJECTED, CANCELLED)
    - Sorted by request date
- **Authorization:** Employee (own requests) or Manager
- **Status Codes:**
    - `200 OK` - Requests retrieved successfully
    - `500 Internal Server Error` - System error

### 4. Get Leave Request by ID

- **Endpoint:** `GET /api/leave-requests/{leaveId}`
- **Description:** Retrieve a specific leave request by its ID
- **Path Parameters:** `leaveId` - Leave Request ID
- **Response:** `ApiResponse<LeaveRequest>`
- **Functionality:**
    - Returns detailed information about a specific leave request
    - Includes employee details, leave type, dates, status, and comments
- **Authorization:** Employee (own requests) or Manager
- **Status Codes:**
    - `200 OK` - Request found and returned
    - `404 Not Found` - Leave request not found
    - `500 Internal Server Error` - System error

### 5. Cancel Leave Request

- **Endpoint:** `PUT /api/leave-requests/{leaveId}/cancel`
- **Description:** Cancel a pending leave request
- **Path Parameters:** `leaveId` - Leave Request ID
- **Query Parameters:** `employeeId` - Employee ID
- **Response:** `ApiResponse<LeaveRequest>`
- **Functionality:**
    - Allows employees to cancel their own pending requests
    - Updates status to CANCELLED
    - Prevents cancellation of approved/rejected requests
    - Adds cancellation timestamp and comment
- **Authorization:** Employee (own requests only)
- **Status Codes:**
    - `200 OK` - Request cancelled successfully
    - `400 Bad Request` - Cannot cancel (not pending or not authorized)
    - `500 Internal Server Error` - System error

---

## ✅ Validation Operations

### 6. Validate Leave Request

- **Endpoint:** `POST /api/leave-requests/validate`
- **Description:** Validate a leave request without saving it
- **Request Body:** `LeaveRequestValidationDTO`
- **Response:** `ApiResponse<ValidationResultDTO>`
- **Functionality:**
    - Performs comprehensive validation without persisting data
    - Checks all business rules and constraints
    - Returns detailed validation results with specific error messages
    - Useful for frontend form validation
- **Authorization:** Any authenticated user
- **Status Codes:**
    - `200 OK` - Validation completed (check response for validation result)
    - `500 Internal Server Error` - System error

### 7. Get Leave Balance

- **Endpoint:** `GET /api/leave-requests/balance/{employeeId}/{leaveTypeId}`
- **Description:** Retrieve leave balance for an employee and leave type
- **Path Parameters:**
    - `employeeId` - Employee ID
    - `leaveTypeId` - Leave Type ID
- **Query Parameters:** `year` - Year (default: 2025)
- **Response:** `ApiResponse<LeaveBalanceDTO>`
- **Functionality:**
    - Returns available leave balance for the specified year
    - Shows allocated, used, and remaining leaves
    - Includes leave type details
- **Authorization:** Employee (own balance) or Manager
- **Status Codes:**
    - `200 OK` - Balance retrieved successfully
    - `404 Not Found` - Balance not found
    - `500 Internal Server Error` - System error

### 8. Check Overlapping Requests

- **Endpoint:** `POST /api/leave-requests/check-overlap`
- **Description:** Check if a leave request overlaps with existing requests
- **Request Body:** `LeaveRequestValidationDTO`
- **Response:** `ApiResponse<Boolean>`
- **Functionality:**
    - Checks for overlapping leave requests for the same employee
    - Returns true if overlaps exist, false otherwise
    - Includes descriptive message about overlap status
- **Authorization:** Any authenticated user
- **Status Codes:**
    - `200 OK` - Overlap check completed
    - `500 Internal Server Error` - System error

---

## 👨‍💼 Manager Operations

### 9. Get Pending Requests for Manager

- **Endpoint:** `GET /api/leave-requests/manager/{managerId}/pending`
- **Description:** Retrieve all pending leave requests for a manager's team
- **Path Parameters:** `managerId` - Manager ID
- **Response:** `ApiResponse<List<LeaveRequest>>`
- **Functionality:**
    - Returns all leave requests with PENDING status
    - Includes requests from all direct reports
    - Sorted by request date (oldest first)
- **Authorization:** Manager
- **Status Codes:**
    - `200 OK` - Pending requests retrieved successfully
    - `500 Internal Server Error` - System error

### 10. Get Leave History for Manager

- **Endpoint:** `GET /api/leave-requests/manager/{managerId}/history`
- **Description:** Retrieve complete leave history for a manager's team
- **Path Parameters:** `managerId` - Manager ID
- **Response:** `ApiResponse<List<LeaveRequest>>`
- **Functionality:**
    - Returns all leave requests (all statuses) for the manager's team
    - Includes approved, rejected, cancelled, and pending requests
    - Useful for reporting and analysis
- **Authorization:** Manager
- **Status Codes:**
    - `200 OK` - Leave history retrieved successfully
    - `500 Internal Server Error` - System error

### 11. Approve Leave Request

- **Endpoint:** `PUT /api/leave-requests/{leaveId}/approve`
- **Description:** Approve a pending leave request
- **Path Parameters:** `leaveId` - Leave Request ID
- **Query Parameters:** `managerId` - Manager ID
- **Response:** `ApiResponse<LeaveRequest>`
- **Functionality:**
    - Changes request status to APPROVED
    - Updates leave balance after approval
    - Records approval date and approving manager
    - Validates manager authorization
- **Authorization:** Manager (for direct reports only)
- **Status Codes:**
    - `200 OK` - Request approved successfully
    - `400 Bad Request` - Cannot approve (not authorized or invalid status)
    - `500 Internal Server Error` - System error

### 12. Reject Leave Request

- **Endpoint:** `PUT /api/leave-requests/{leaveId}/reject`
- **Description:** Reject a pending leave request
- **Path Parameters:** `leaveId` - Leave Request ID
- **Query Parameters:**
    - `managerId` - Manager ID
    - `comment` - Rejection reason/comment
- **Response:** `ApiResponse<LeaveRequest>`
- **Functionality:**
    - Changes request status to REJECTED
    - Records rejection date, manager, and comment
    - Does not affect leave balance
    - Validates manager authorization
- **Authorization:** Manager (for direct reports only)
- **Status Codes:**
    - `200 OK` - Request rejected successfully
    - `400 Bad Request` - Cannot reject (not authorized or invalid status)
    - `500 Internal Server Error` - System error

### 13. Update Leave Request by Manager

- **Endpoint:** `PUT /api/leave-requests/manager/{leaveId}/update`
- **Description:** Update leave request details as a manager
- **Path Parameters:** `leaveId` - Leave Request ID
- **Query Parameters:**
    - `managerId` - Manager ID
    - `leaveTypeId` - New Leave Type ID (optional)
    - `startDate` - New Start Date (optional)
    - `endDate` - New End Date (optional)
- **Response:** `ApiResponse<LeaveRequest>`
- **Functionality:**
    - Allows managers to modify leave request details
    - Recalculates days requested if dates are changed
    - Updates leave type if specified
    - Validates manager authorization
- **Authorization:** Manager (for direct reports only)
- **Status Codes:**
    - `200 OK` - Request updated successfully
    - `400 Bad Request` - Cannot update (not authorized)
    - `500 Internal Server Error` - System error

---

## 📊 Response Format

All API endpoints return responses in a consistent format using the `ApiResponse<T>` wrapper:

```json
{
  "success": boolean,
  "message": "string",
  "data": T | null
}
```

### Success Response Example:

```json
{
  "success": true,
  "message": "Leave application submitted successfully",
  "data": {
    "leaveId": "LR12345",
    "employee": {...},
    "leaveType": {...},
    "startDate": "2025-08-01",
    "endDate": "2025-08-05",
    "daysRequested": 5,
    "status": "PENDING",
    "reason": "Family vacation"
  }
}
```

### Error Response Example:

```json
{
  "success": false,
  "message": "Insufficient Casual Leave balance. Available: 2.0 days, Requested: 5.0 days",
  "data": null
}
```

---

## ⚠️ Error Handling

### Common HTTP Status Codes:

- **200 OK** - Request processed successfully
- **400 Bad Request** - Invalid request data or business rule violation
- **401 Unauthorized** - Authentication required
- **403 Forbidden** - Insufficient permissions
- **404 Not Found** - Resource not found
- **500 Internal Server Error** - System error

### Validation Error Response:

For validation endpoints, the response includes detailed validation results:

```json
{
  "success": true,
  "message": "Validation completed",
  "data": {
    "valid": false,
    "employeeId": "EMP001",
    "employeeName": "John Doe",
    "requestedDays": 5.0,
    "errors": [
      "Insufficient Casual Leave balance. Available: 2.0 days, Requested: 5.0 days",
      "Leave request requires 3 days advance notice"
    ]
  }
}
```

---

## 🔐 Authorization & Security

### Role-Based Access:

- **Employee**: Can manage their own leave requests
- **Manager**: Can manage leave requests for direct reports
- **Admin**: Full access to all operations

### Security Features:

- Manager authorization validation for team operations
- Employee can only access/modify their own requests
- Status-based operation restrictions (e.g., can't modify approved requests)
- Input validation and sanitization
- Transaction management for data consistency

---

## 📝 Business Rules Implemented

### Leave Validation Rules:

1. **Date Constraints**: End date must be after start date, advance notice requirements
2. **Balance Validation**: Sufficient leave balance (unless negative balance allowed)
3. **Overlap Prevention**: No overlapping leave requests for same employee
4. **Leave Type Rules**:
    - Maternity Leave: Max 2 times, standard 180 days
    - Paternity Leave: Exactly 5 days, max 2 times with 1-year gap
    - Half-day restrictions based on leave type
5. **Waiting Period**: New employees must wait specified days before applying
6. **Past Date Restrictions**: Configurable past date limits per leave type

### Status Workflow:

```
PENDING → APPROVED/REJECTED/CANCELLED
```

- Only PENDING requests can be modified or cancelled
- Approved requests update leave balance
- Rejected/Cancelled requests don't affect balance

---

## 🚀 Getting Started

### Base Configuration:

- **Server**: Spring Boot application
- **Database**: JPA/Hibernate with relational database
- **Authentication**: JWT/Session-based (configure as needed)
- **Content-Type**: `application/json`

### Sample Request Headers:

```
Content-Type: application/json
Authorization: Bearer <token>
```

This API documentation provides a complete reference for integrating with the Employee Leave Management System. All
endpoints follow RESTful conventions and return consistent response formats for easy integration.
