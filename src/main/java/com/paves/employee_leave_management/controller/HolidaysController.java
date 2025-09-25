package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.service.HolidaysServiceImple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidaysController {

    @Autowired
    private HolidaysServiceImple holidaysService;

    // 🔹 Get all holidays
    @GetMapping("/all")
    @PreAuthorize("hasRole('HR','GENERAL','MANAGER')")
    public ResponseEntity<List<Holidays>> getAllHolidays() {
        return holidaysService.getAllHolidays();
    }

    // 🔹 Get holiday by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Holidays> getHolidayById(@PathVariable Long id) {
        return holidaysService.getHolidayById(id);
    }

    // 🔹 Add new holiday
    @PostMapping("/add")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> addHoliday(@RequestBody List<Holidays> holidays) {
        return holidaysService.addHoliday(holidays);
    }

    // 🔹 Update holiday
    @PutMapping("/update")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> updateHoliday(@RequestBody Holidays holidays) {// ensure correct holiday is updated
        return holidaysService.updateHoliday(holidays);
    }

    // 🔹 Delete holiday
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteHoliday(@PathVariable Long id) {
        return holidaysService.deleteHoliday(id);
    }

    @GetMapping("/year/{year}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<Holidays>> getHolidaysByYear(@PathVariable int year) {
        return holidaysService.getHolidaysByYear(year);
    }

    // 🔹 Delete all holidays for a specific year
    @DeleteMapping("/year/{year}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<String> deleteHolidaysByYear(@PathVariable int year) {
        return holidaysService.deleteHolidaysByYear(year);
    }

}

