package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.BlockStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class UpdateLeaveBlockRequest {
        private String type;  // "UPDATE" or "UNBLOCK"
        private String blockId;
        private Integer year;
        private String reason;
        private LocalDate startDate;
        private LocalDate endDate;
        private BlockStatus status;
        private List<MappingUpdateDto> mappingUpdates; // normal block updates
        private List<EmployeeUnblockRequest> unblockedRequests; // new addition ✅
        private Updates updates;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EmployeeUnblockRequest {
            private String employeeId;
            private List<String> leaveTypeIds;
        }

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Updates{
            private String reason;
            private LocalDate startDate;
            private LocalDate endDate;
            private BlockStatus status;
        }
    }

