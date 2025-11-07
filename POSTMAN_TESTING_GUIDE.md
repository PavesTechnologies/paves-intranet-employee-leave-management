# Comprehensive Postman Testing Guide - Leave Management System

## 🚀 Setup Instructions

### Environment Variables

Create a Postman environment with these variables (based on actual DB data):

```
baseUrl: http://localhost:8080
employeeId: PAVEMP60F49
managerId: PAVEMP60DA9
leaveId: LR2681
leaveTypeId: L-PL
balanceId: BAL002DC
year: 2025
newEmployeeId: {{employeeId_from_registration}}
```

---

## 📋 Complete API Endpoints Testing

## 1. 🧑‍💼 Employee Controller (`/api/employee`)

### 1.1 Register Employee

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/employee/register`
- **Body (JSON):** ⚠️ **Note: employeeId is auto-generated, do NOT include it in request**

```json
{
  "firstName": "Test",
  "lastName": "Employee",
  "email": "test.employee@paves.com",
  "phone": "9999999999",
  "salary": 75000.00,
  "hireDate": "2025-01-15",
  "jobTitle": "Software Engineer",
  "gender": "Male",
  "password": "test123",
  "manager": {
    "employeeId": "PAVEMP60DA9"
  }
}
```

**Important Testing Notes:**

- ✅ Employee ID will be auto-generated in format: `PAVEMP` + 5 random uppercase chars
- ✅ Save the generated employeeId from response for subsequent tests
- ✅ Use Postman Tests tab to extract employeeId: `pm.environment.set("newEmployeeId", pm.response.json().employeeId);`

**Edge Cases to Test:**

- ✅ Valid employee registration (verify auto-generated ID in response)
- ❌ Duplicate email address (try: ajay.smith@example.com)
- ❌ Missing required fields (firstName, lastName, email)
- ❌ Invalid email format
- ❌ Future hire date
- ❌ Invalid manager ID (try: PAVEMP00000)
- ❌ Invalid phone number format
- ❌ Negative salary
- ❌ Including employeeId in request (should be ignored or cause error)

### 1.2 Update Employee

- **Method:** `PUT`
- **URL:** `{{baseUrl}}/api/employee/update/{{employeeId}}`
- **Body:** Same as registration (without employeeId)

**Edge Cases to Test:**

- ✅ Valid employee update (use existing: PAVEMP60F49)
- ✅ Update newly created employee (use: {{newEmployeeId}})
- ❌ Non-existent employee ID (try: PAVEMP00000)
- ❌ Invalid data formats
- ❌ Unauthorized access

---

## 2. 🏷️ Leave Type Controller (`/api/leave`)

### 2.1 Add Leave Type

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/leave/add-leave-type`
- **Body (JSON):**

```json
{
  "leaveTypeId": "L-TEST",
  "leaveTypeName": "Test Leave",
  "maxDaysAllowed": 10,
  "carryForwardAllowed": true,
  "maxCarryForwardDays": 2,
  "description": "Test leave type for API testing"
}
```

**Edge Cases to Test:**

- ✅ Valid leave type creation
- ❌ Duplicate leave type ID (try: L-PL, L-EL, L-SL)
- ❌ Negative max days
- ❌ Missing required fields

### 2.2 Get All Leave Types

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave/get-all-leave-types`

**Expected Response:** Should include existing types like:

- COMPOFF (Compensatory leave)
- L-H (Holiday Leave)
- L-EL (Earned Leave)
- L-SL (Sick Leave)
- L-ML (Maternity Leave)

### 2.3 Update Leave Type

- **Method:** `PUT`
- **URL:** `{{baseUrl}}/api/leave/update-leave-type`
- **Body:** Same as add leave type

**Edge Cases to Test:**

- ✅ Valid leave type update (try updating L-TEST)
- ❌ Non-existent leave type

---

## 3. 💰 Leave Balance Controller (`/api/leave-balance`)

### 3.1 Generate Leave Balance

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/leave-balance/generate/{{employeeId}}`

**Edge Cases to Test:**

- ✅ Valid balance generation (try: PAVEMP99999)
- ❌ Non-existent employee (try: PAVEMP00000)
- ❌ Balance already exists (try: PAVEMP60F49)

### 3.2 Carry Forward Process

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/leave-balance/carryforward`

**Edge Cases to Test:**

- ✅ Successful carry forward
- ✅ No balances to carry forward

### 3.3 Get Leave Balance by ID

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-balance/{{balanceId}}`

**Edge Cases to Test:**

- ✅ Valid balance retrieval (try: BAL002DC, BAL07E0, BAL6B47)
- ❌ Non-existent balance ID (try: BAL00000)

### 3.4 Get All Leave Balances

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-balance`

**Expected Response:** Should return balances with IDs like BAL002DC, BAL07E0, etc.

### 3.5 Get Leave Balances by Employee

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-balance/employee/{{employeeId}}`

**Edge Cases to Test:**

- ✅ Valid employee balances (try: PAVEMP60F49, PAVEMP1FC86)
- ❌ Non-existent employee (try: PAVEMP00000)
- ✅ Employee with no balances

### 3.6 Get Leave Balance by Employee and Year

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-balance/employee/{{employeeId}}/year/{{year}}`

**Edge Cases to Test:**

- ✅ Valid year balance (try: PAVEMP60F49/2025)
- ❌ Invalid year format (try: 25, 2025-01-01)
- ❌ Future year (try: 2030)
- ✅ No balance for year (try: 2020)

---

## 4. 📝 Leave Request Controller (`/api/leave-requests`)

### 4.1 Apply for Leave

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/leave-requests/apply`
- **Body (JSON):**

```json
{
  "employeeId": "PAVEMP60F49",
  "leaveTypeId": "L-PL",
  "startDate": "2025-08-01",
  "endDate": "2025-08-05",
  "reason": "Family vacation - API test",
  "emergencyContact": "9876543210"
}
```

**Edge Cases to Test:**

- ✅ Valid leave application
- ❌ Invalid date range (endDate before startDate)
- ❌ Past dates (try: 2024-01-01 to 2024-01-05)
- ❌ Insufficient leave balance (try requesting 200 days of L-PL)
- ❌ Overlapping leave requests (apply for same dates twice)
- ❌ Weekend/holiday dates
- ❌ Advance notice violation (apply for tomorrow)
- ❌ Non-existent employee (try: PAVEMP00000)
- ❌ Non-existent leave type (try: L-INVALID)
- ❌ Maternity leave without eligibility (male employee requesting L-ML)
- ❌ Leave request exceeding max allowed days

### 4.2 Update Leave Request (Employee)

- **Method:** `PUT`
- **URL:** `{{baseUrl}}/api/leave-requests/employee/update`
- **Body (JSON):**

```json
{
  "leaveId": "LR2681",
  "employeeId": "PAVEMP60F49",
  "leaveTypeId": "L-PL",
  "startDate": "2025-08-02",
  "endDate": "2025-08-06",
  "reason": "Updated vacation dates - API test",
  "emergencyContact": "9876543210"
}
```

**Edge Cases to Test:**

- ✅ Valid pending request update
- ❌ Update approved/rejected request (try: LR3E18 which is APPROVED)
- ❌ Update non-existent request (try: LR00000)
- ❌ Unauthorized employee update
- ❌ Invalid updated dates

### 4.3 Get Employee Leave Requests

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-requests/employee/{{employeeId}}`

**Edge Cases to Test:**

- ✅ Valid employee requests (try: PAVEMP60F49, PAVEMP230GF)
- ❌ Non-existent employee (try: PAVEMP00000)
- ✅ Employee with no requests

### 4.4 Get Leave Request by ID

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-requests/{{leaveId}}`

**Edge Cases to Test:**

- ✅ Valid leave request retrieval (try: LR2681, LR3E18, LR1F7F)
- ❌ Non-existent leave ID (try: LR00000)

### 4.5 Cancel Leave Request

- **Method:** `PUT`
- **URL:** `{{baseUrl}}/api/leave-requests/{{leaveId}}/cancel?employeeId={{employeeId}}`

**Edge Cases to Test:**

- ✅ Valid pending request cancellation
- ❌ Cancel approved/rejected request (try: LR3E18)
- ❌ Cancel non-existent request (try: LR00000)
- ❌ Unauthorized cancellation

### 4.6 Validate Leave Request

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/leave-requests/validate`
- **Body:** Same as apply leave

**Edge Cases to Test:**

- ✅ Valid request validation
- ❌ All validation failure scenarios (same as apply)

### 4.7 Get Leave Balance

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-requests/balance/{{employeeId}}/{{leaveTypeId}}/{{year}}`

**Edge Cases to Test:**

- ✅ Valid balance retrieval (try: PAVEMP60F49/L-PL/2025)
- ❌ Non-existent employee/leave type
- ❌ Invalid year

### 4.8 Get Pending Requests for Manager

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-requests/manager/{{managerId}}/pending`

**Edge Cases to Test:**

- ✅ Valid manager pending requests (try: PAVEMP60DA9)
- ❌ Non-existent manager (try: PAVEMP00000)
- ✅ Manager with no pending requests

### 4.9 Get All Requests for Manager

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/leave-requests/manager/{{managerId}}`

**Edge Cases to Test:**

- ✅ Valid manager all requests (try: PAVEMP60DA9)
- ❌ Non-existent manager
- ✅ Manager with no requests

### 4.10 Approve Leave Request

- **Method:** `PUT`
- **URL:** `{{baseUrl}}/api/leave-requests/{{leaveId}}/approve?managerId={{managerId}}&comments=Approved via API test`

**Edge Cases to Test:**

- ✅ Valid approval (use a PENDING request)
- ❌ Approve non-pending request (try: LR3E18 which is already APPROVED)
- ❌ Unauthorized manager approval
- ❌ Non-existent request

### 4.11 Reject Leave Request

- **Method:** `PUT`
- **URL:**
  `{{baseUrl}}/api/leave-requests/{{leaveId}}/reject?managerId={{managerId}}&comments=Rejected - insufficient staffing`

**Edge Cases to Test:**

- ✅ Valid rejection (use a PENDING request)
- ❌ Reject non-pending request
- ❌ Unauthorized manager rejection
- ❌ Non-existent request

### 4.12 Update Leave Request (Manager)

- **Method:** `PUT`
- **URL:** `{{baseUrl}}/api/leave-requests/manager/update`
- **Body (JSON):**

```json
{
  "leaveId": "LR2681",
  "managerId": "PAVEMP60DA9",
  "status": "APPROVED",
  "managerComments": "Approved with conditions - API test"
}
```

**Edge Cases to Test:**

- ✅ Valid manager update
- ❌ Unauthorized manager update
- ❌ Invalid status transition
- ❌ Non-existent request

---

## 🧪 Advanced Testing Scenarios

### Workflow Testing

1. **Complete Leave Application Flow:**
    - Register employee (PAVEMP99999) → Generate balance → Apply leave → Manager approve → Verify balance deduction

2. **Leave Validation Chain:**
    - Apply overlapping leaves for PAVEMP60F49 → Verify rejection
    - Apply leave exceeding balance → Verify rejection
    - Apply leave with insufficient advance notice → Verify rejection

3. **Manager Operations Flow:**
    - Get pending requests for PAVEMP60DA9 → Approve/Reject → Verify status updates

### Data Integrity Testing

- Apply multiple leaves and verify balance calculations
- Test carry forward process with existing balances
- Verify leave type constraints are enforced

### Error Handling Testing

- Send malformed JSON
- Send requests with missing headers
- Test with invalid content types
- Test with extremely large payloads

---

## 📊 Test Data Sets (Based on Actual DB)

### Valid Test Data

```json
{
  "employees": [
    {"employeeId": "PAVEMP60F49", "firstName": "ajay", "lastName": "kumar", "email": "ajay.smith@example.com"},
    {"employeeId": "PAVEMP1FC86", "firstName": "mohan", "lastName": "sahu", "email": "mohan@email.com"},
    {"employeeId": "PAVEMP230GF", "firstName": "sruthi", "lastName": "p", "email": "s@gmail.com"},
    {"employeeId": "PAVEMP60DA9", "firstName": "Manager", "lastName": "User", "email": "manager@company.com"}
  ],
  "leaveTypes": [
    {"leaveTypeId": "L-PL", "leaveTypeName": "Privilege Leave", "maxDaysAllowed": 182},
    {"leaveTypeId": "L-EL", "leaveTypeName": "Earned Leave", "maxDaysAllowed": 25},
    {"leaveTypeId": "L-SL", "leaveTypeName": "Sick Leave", "maxDaysAllowed": 12},
    {"leaveTypeId": "L-ML", "leaveTypeName": "Maternity Leave", "maxDaysAllowed": 180},
    {"leaveTypeId": "COMPOFF", "leaveTypeName": "Compensatory Leave", "maxDaysAllowed": 10}
  ],
  "leaveBalances": [
    {"balanceId": "BAL002DC", "employeeId": "PAVEMP60F49", "leaveTypeId": "L-PL", "totalLeaves": 182, "remainingLeaves": 182},
    {"balanceId": "BAL6B47", "employeeId": "PAVEMP230GF", "leaveTypeId": "L-EL", "totalLeaves": 6.25, "remainingLeaves": 6.25}
  ],
  "leaveRequests": [
    {"leaveId": "LR2681", "employeeId": "PAVEMP60F49", "leaveTypeId": "L-PL", "status": "PENDING"},
    {"leaveId": "LR3E18", "employeeId": "PAVEMP60F49", "leaveTypeId": "L-SL", "status": "APPROVED"}
  ]
}
```

### Invalid Test Data

```json
{
  "invalidDates": ["2025-13-01", "2025-02-30", "invalid-date"],
  "invalidEmployeeIds": ["", null, "PAVEMP00000", "INVALID"],
  "invalidEmails": ["invalid-email", "@company.com", "user@"],
  "invalidLeaveTypes": ["L-INVALID", "", null, "NONEXISTENT"]
}
```

---

## ✅ Testing Checklist

### Pre-Testing Setup

- [ ] Start the application server
- [ ] Import Postman collection
- [ ] Set up environment variables with actual DB values
- [ ] Verify existing test data (employees: PAVEMP60F49, PAVEMP1FC86, etc.)

### Functional Testing

- [ ] Test all CRUD operations for each entity
- [ ] Verify all validation rules with actual employee IDs
- [ ] Test all business logic scenarios
- [ ] Verify error responses and status codes

### Edge Case Testing

- [ ] Test boundary conditions with real balance values
- [ ] Test with invalid data using non-existent IDs
- [ ] Test authorization scenarios with actual manager-employee relationships
- [ ] Test concurrent operations

### Integration Testing

- [ ] Test complete workflows using actual employee data
- [ ] Verify data consistency across operations
- [ ] Test cascade operations

### Performance Testing

- [ ] Test with large datasets
- [ ] Test concurrent requests
- [ ] Monitor response times

---

## 🔧 Postman Collection Structure

```
Leave Management API Tests/
├── Setup/
│   ├── Create Test Employee (PAVEMP99999)
│   ├── Create Leave Types
│   └── Generate Leave Balances
├── Employee Operations/
│   ├── Register Employee
│   └── Update Employee (PAVEMP60F49)
├── Leave Type Operations/
│   ├── Add Leave Type
│   ├── Get All Leave Types (L-PL, L-EL, L-SL, etc.)
│   └── Update Leave Type
├── Leave Balance Operations/
│   ├── Generate Balance
│   ├── Get Balances (BAL002DC, BAL6B47, etc.)
│   └── Carry Forward
├── Leave Request Operations/
│   ├── Apply Leave (PAVEMP60F49)
│   ├── Update Request (LR2681)
│   ├── Cancel Request
│   ├── Manager Operations (PAVEMP60DA9)
│   └── Validation Tests
└── Edge Cases/
    ├── Invalid Data Tests (PAVEMP00000, L-INVALID)
    ├── Authorization Tests
    └── Boundary Tests
```

## 🎯 Key Testing Focus Areas

### Real Data Validation

- Use actual employee IDs from your database
- Test with existing leave balances and their actual values
- Verify leave requests work with real leave type IDs
- Test manager operations with actual manager-employee relationships

### Business Logic with Real Constraints

- Test leave applications against actual remaining balances
- Verify overlapping request detection with existing requests
- Test advance notice requirements with current dates
- Validate leave type specific rules (ML for female employees only, etc.)

This updated guide now uses your actual database structure and values for realistic testing!
