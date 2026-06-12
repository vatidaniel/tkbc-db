package io.github.vatisteve.tkbc.db.service.sp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for StoredProcedureParameterIn
 */
class StoredProcedureParameterInTest {

    @Test
    void testNullNameThrowsException() {
        // Apache Validate may throw NPE or IllegalArgumentException; accept any runtime exception
        assertThrows(RuntimeException.class, () -> new StoredProcedureParameterIn<>(null, 1));
    }

    @Test
    void testBlankNameThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new StoredProcedureParameterIn<>("  ", 1));
    }

    @Test
    void testNullValueThrowsException() {
        assertThrows(RuntimeException.class, () -> new StoredProcedureParameterIn<Object>("p", null));
    }

    @Test
    void testGettersAndTypeReturnCorrectClass() {
        StoredProcedureParameterIn<Integer> p = new StoredProcedureParameterIn<>("age", 25);
        assertEquals("age", p.getName());
        assertEquals(Integer.valueOf(25), p.getValue());
        assertEquals(Integer.class, p.getType());
    }

    @Test
    void testExplicitTypeConstructorAllowsNullValue() {
        StoredProcedureParameterIn<String> p = new StoredProcedureParameterIn<>("note", null, String.class);
        assertEquals("note", p.getName());
        assertNull(p.getValue());
        assertEquals(String.class, p.getType());
    }

    @Test
    void testExplicitTypeConstructorRejectsNullType() {
        assertThrows(RuntimeException.class, () -> new StoredProcedureParameterIn<>("note", "x", null));
    }
}
