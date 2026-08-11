package com.paves.employee_leave_management.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployeeCdcEvent {

    @JsonProperty("employee_uuid")
    private String employeeUuid;

    @JsonProperty("employee_id")
    private String employeeId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("middle_name")
    private String middleName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("work_email")
    private String workEmail;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("contact_number")
    private String contactNumber;

    @JsonProperty("joining_date")
    private String joiningDate;

    @JsonProperty("designation_uuid")
    private String designationUuid;

    @JsonProperty("employment_status")
    private String employmentStatus;

    @JsonProperty("employment_type")
    private String employmentType;

    @JsonProperty("reporting_manager_uuid")
    private String reportingManagerUuid;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("__op")
    private String op;

    @JsonProperty("__ts_ms")
    private Long tsMs;

    @JsonProperty("__deleted")
    private String deleted;
}