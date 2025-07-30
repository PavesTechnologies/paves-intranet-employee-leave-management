package com.paves.employee_leave_management.controller;


import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.dto.LeaveCompoffUpdateStatusDTO;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveStatusCompoff;
import com.paves.employee_leave_management.service.LeaveCompoffServiceImpl;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/compoff")
@RequiredArgsConstructor
public class LeaveCompoffController {

    @Autowired
    LeaveCompoffSerivceInterface compoffService;

    @PostMapping("/request")
    public String requestCompoff(@RequestBody LeaveCompoffRequestDTO dto) {
        compoffService.requestCompoff(dto);
        return "Compoff requested successfully.";
    }

    @PutMapping("/update-status")
    public String updateCompoffStatus(@RequestBody LeaveCompoffUpdateStatusDTO dto) {
        compoffService.updateCompoffStatus(dto);
        return "Compoff status updated successfully.";
    }

    @GetMapping("/employee/{employeeId}")
    public List<LeaveCompoff> getByEmployee(@PathVariable String employeeId) {
        return compoffService.getCompoffsByEmployee(employeeId);
    }

    @GetMapping("/manager/{managerId}/status/{status}")
    public List<LeaveCompoff> getByManagerAndStatus(@PathVariable String managerId,
                                                    @PathVariable LeaveStatusCompoff status) {
        return compoffService.getCompoffsByManagerAndStatus(managerId, status);
    }

}
