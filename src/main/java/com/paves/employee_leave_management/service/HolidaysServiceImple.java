package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Holidays;
import com.paves.employee_leave_management.globalExceptionHandler.HolidayExceptionHandler;
import com.paves.employee_leave_management.repo.HolidayRepo;
import com.paves.employee_leave_management.serviceInterface.HolidaysServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;


@Service
public class HolidaysServiceImple implements HolidaysServiceInterface {

    @Autowired
    HolidayRepo holidayRepo;
    @Override
    public ResponseEntity<List<Holidays>> getAllHolidays() {

        List<Holidays> holidays = holidayRepo.findAll();
        if(holidays.isEmpty())
            throw new HolidayExceptionHandler("No holidays found");
        return ResponseEntity.ok(holidays);
    }

    @Override
    public ResponseEntity<Holidays> getHolidayById(Long id) {
        Holidays holiday = holidayRepo.findById(id)
                .orElseThrow(() -> new HolidayExceptionHandler("No holiday found for this id"));

        return ResponseEntity.ok(holiday);

    }

    @Override
    public ResponseEntity<String> addHoliday(List<Holidays> holidays) {
        for (Holidays holiday : holidays) {
            boolean exists = holidayRepo.existsByHolidayDateAndStateAndYear(
                    holiday.getHolidayDate(),
                    holiday.getState(),
                    holiday.getYear()
            );

            if (exists) {
                throw new HolidayExceptionHandler("Holiday already exists for date: "
                        + holiday.getHolidayDate() + ", state: " + holiday.getState()
                        + ", year: " + holiday.getYear());
            }
        }

        holidayRepo.saveAll(holidays);
        return ResponseEntity.ok("Holidays added successfully");
    }


    @Override
    public ResponseEntity<String> updateHoliday(Holidays holidays) {
        Holidays existingHoliday = holidayRepo.findById(holidays.getHolidayId())
                .orElseThrow(() -> new HolidayExceptionHandler("No holiday found for this id"));
        holidayRepo.save(holidays);
        return ResponseEntity.ok("Holiday updated successfully");
    }

    @Override
    public ResponseEntity<String> deleteHoliday(Long id) {
        holidayRepo.findById(id).orElseThrow(() -> new HolidayExceptionHandler("No holiday found for this id"));
        holidayRepo.deleteById(id);
        return ResponseEntity.ok("Holiday deleted successfully");
    }

    @Override
    public ResponseEntity<List<Holidays>> getHolidaysByYear(int year) {
        List<Holidays> holidays=holidayRepo.findByYear(year).orElseThrow(() -> new HolidayExceptionHandler("No holidays found for this year"));
        return ResponseEntity.ok(holidays);
    }

    @Override
    public ResponseEntity<String> deleteHolidaysByYear(int year) {
        holidayRepo.findByYear(year).orElseThrow(() -> new HolidayExceptionHandler("No holidays found for this year"));
        holidayRepo.deleteByYear(year);
        return ResponseEntity.ok("Holidays deleted successfully for year: " + year);
    }

    @Override
    public ResponseEntity<String> createHolidaysForCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        int lastYear = currentYear - 1;

        List<Holidays> lastYearHolidays = getHolidaysByYear(lastYear).getBody();

        List<Holidays> newYearHolidays = lastYearHolidays.stream()
                .map(h -> {
                    Holidays newHoliday = new Holidays();
                    newHoliday.setHolidayName(h.getHolidayName());
                    newHoliday.setHolidayDate(h.getHolidayDate().withYear(currentYear));
                    newHoliday.setHolidayDescription(h.getHolidayDescription());
                    newHoliday.setType(h.getType());
                    newHoliday.setState(h.getState());
                    return newHoliday;
                })
                .toList();

        holidayRepo.saveAll(newYearHolidays);

        return ResponseEntity.ok("Holidays created successfully for year: " + currentYear);
    }

    @Override
    public ResponseEntity<String> deleteHolidaysThreeYearsAgo() {
        int threeYearsAgo = LocalDate.now().getYear() - 3;
        deleteHolidaysByYear(threeYearsAgo);
        return ResponseEntity.ok("Holidays deleted successfully for year: " + threeYearsAgo);
    }

}
