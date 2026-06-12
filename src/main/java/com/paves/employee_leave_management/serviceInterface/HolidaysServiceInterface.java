package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.HolidayNameDateDto;
import com.paves.employee_leave_management.entities.Holidays;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public interface HolidaysServiceInterface {
    ResponseEntity<List<Holidays>> getAllHolidays();

    ByteArrayInputStream createHolidayTemplate() throws IOException, SQLException;

    ResponseEntity<Holidays> getHolidayById(Long id);

    ResponseEntity<String> addHoliday(List<Holidays> holidays);

    ResponseEntity<String> updateHoliday(Holidays holidays);

    ResponseEntity<String> deleteHoliday(Long id);

    List<Holidays> getHolidaysByYear(int year);

    ResponseEntity<String> deleteHolidaysByYear(int year);

    ResponseEntity<String> createHolidaysForCurrentYear();

    ResponseEntity<String> deleteHolidaysThreeYearsAgo();

    void importHolidaysFromExcel(MultipartFile file) throws IOException, IOException;

    ApiResponse<List<HolidayNameDateDto>> getHolidaysByStateAndCountry(String state, String country, int year);

    List<Holidays> getHolidaysByMonth(int month);
}
