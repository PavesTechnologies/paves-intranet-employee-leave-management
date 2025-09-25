package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.HolidayType;
import com.paves.employee_leave_management.entities.Holidays;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface HolidaysServiceInterface {
    ResponseEntity<List<Holidays>> getAllHolidays();
    ResponseEntity<Holidays> getHolidayById(Long id);
    ResponseEntity<String> addHoliday(List<Holidays> holidays);
    ResponseEntity<String> updateHoliday(Holidays holidays);
    ResponseEntity<String> deleteHoliday(Long id);

    ResponseEntity<List<Holidays>> getHolidaysByYear(int year);

    ResponseEntity<String> deleteHolidaysByYear(int year);

    ResponseEntity<String> createHolidaysForCurrentYear();

    ResponseEntity<String> deleteHolidaysThreeYearsAgo();
}
