package org.globsframework.shared.mem.impl.field.dataaccess;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateDataAccessTest {

    @Test
    public void testDate() {
        assertEquals(0, DateDataAccess.compareDate(LocalDate.of(1000, 2, 2),
                DateDataAccess.toLong(1000, 2, 2)));

        assertEquals(-1, DateDataAccess.compareDate(LocalDate.of(1000, 2, 2),
                DateDataAccess.toLong(1000, 2, 3)));

        assertEquals(-1, DateDataAccess.compareDate(LocalDate.of(1000, 2, 2),
                DateDataAccess.toLong(1000, 3, 2)));

        assertEquals(-1, DateDataAccess.compareDate(LocalDate.of(1000, 2, 2),
                DateDataAccess.toLong(1001, 2, 2)));

        assertEquals(-1, DateDataAccess.compareDate(LocalDate.of(-1000, 2, 2),
                DateDataAccess.toLong(-999, 2, 2)));

        assertEquals(0, DateDataAccess.compareDate(LocalDate.of(-1000, 2, 2),
                DateDataAccess.toLong(-1000, 2, 2)));

        assertEquals(0, DateDataAccess.compareDate(LocalDate.of(-1000, 2, 2),
                DateDataAccess.toLong(-1000, 2, 2)));
    }
}