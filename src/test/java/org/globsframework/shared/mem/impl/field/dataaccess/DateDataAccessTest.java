package org.globsframework.shared.mem.impl.field.dataaccess;

import junit.framework.TestCase;

import java.time.LocalDate;

public class DateDataAccessTest extends TestCase {

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