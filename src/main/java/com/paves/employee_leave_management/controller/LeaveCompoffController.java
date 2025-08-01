package com.paves.employee_leave_management.controller;


import com.paves.employee_leave_management.dto.*;
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

    @PutMapping("/approve")
    public String approveCompoff(@RequestBody ApproveRejectCompoffDTO dto) {
        compoffService.approveCompoff(dto.getCompoffId());
        return "Compoff approved successfully.";
    }

    @PutMapping("/reject")
    public String rejectCompoff(@RequestBody ApproveRejectCompoffDTO dto) {
        compoffService.rejectCompoff(dto.getCompoffId());
        return "Compoff rejected successfully.";
    }

    @GetMapping("/employee/{employeeId}")
    public List<LeaveCompoff> getByEmployee(@PathVariable String employeeId) {
        return compoffService.getCompoffsByEmployee(employeeId);
    }

    @PostMapping("/manager/status")
    public List<LeaveCompoff> getByManagerAndStatus(@RequestBody ManagerCompoffStatusDTO dto) {
        return compoffService.getCompoffsByManagerAndStatus(dto.getManagerId(), dto.getStatus());
    }

    @PostMapping("/manager/pending")
    public List<LeaveCompoff> getPendingCompoffsByManager(@RequestBody ManagerPendingCompoffDTO dto) {
        return compoffService.getPendingCompoffsForManager(dto.getManagerId());
    }

}
