package com.paves.employee_leave_management.helper;

import com.paves.employee_leave_management.dto.ManagerQueryDTO;
import com.paves.employee_leave_management.entities.LeaveRequest;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;

public class LeaveRequestSpecification {

    public static Specification<LeaveRequest> filterByManagerQuery(ManagerQueryDTO queryDTO) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ✅ Mandatory: Manager filter
            predicates.add(cb.equal(
                    root.get("employee").get("manager").get("employeeId"),
                    queryDTO.getManagerId()
            ));

            // ✅ Status logic
            if (queryDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            } else {
                predicates.add(cb.equal(root.get("status"), "PENDING"));
            }

            // ✅ Employee filter
            if (queryDTO.getEmployeeId() != null) {
                predicates.add(cb.equal(
                        root.get("employee").get("employeeId"),
                        queryDTO.getEmployeeId()
                ));
            }

            // ✅ Leave type filter
            if (queryDTO.getLeaveTypeId() != null) {
                predicates.add(cb.equal(
                        root.get("leaveType").get("leaveTypeId"),
                        queryDTO.getLeaveTypeId()
                ));
            }

            // ✅ Year filter (BEST for your case)
            if (queryDTO.getYear() != null) {
                predicates.add(cb.equal(root.get("year"), queryDTO.getYear()));
            }

            // ✅ Month filter (using startDate)
            if (queryDTO.getMonth() != null) {
                predicates.add(cb.equal(
                        cb.function("MONTH", Integer.class, root.get("startDate")),
                        queryDTO.getMonth()
                ));
            }

            // ✅ Date range filter (optional)
            if (queryDTO.getFromDate() != null && queryDTO.getToDate() != null) {
                predicates.add(cb.between(
                        root.get("startDate"),
                        queryDTO.getFromDate(),
                        queryDTO.getToDate()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
