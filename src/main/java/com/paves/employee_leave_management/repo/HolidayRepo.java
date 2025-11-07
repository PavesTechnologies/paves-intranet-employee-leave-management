package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Holidays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepo extends JpaRepository<Holidays, Long> {
    Optional<List<Holidays>> findByYear(int year);

    void deleteByYear(int year);

    boolean existsByHolidayDateAndStateAndYear(LocalDate holidayDate, String state, int year);

    Optional<Holidays> findByHolidayDateAndYear(LocalDate date, int year);

    List<Holidays> findByStateAndCountryAndYear(String state, String country, int year);

    @Query("SELECT h FROM Holidays h WHERE FUNCTION('MONTH', h.holidayDate) = :month")
    List<Holidays> findByMonth(@Param("month") int month);
}
