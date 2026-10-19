package fr.oesphy.pyfish.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventResultTest {

    @Test
    void testEventResultValues() {
        assertEquals(4, EventResult.values().length);
        assertEquals(EventResult.PASS, EventResult.valueOf("PASS"));
        assertEquals(EventResult.CANCEL, EventResult.valueOf("CANCEL"));
        assertEquals(EventResult.SUCCESS, EventResult.valueOf("SUCCESS"));
        assertEquals(EventResult.CONSUME, EventResult.valueOf("CONSUME"));
    }

    @Test
    void testStopsPropagation() {
        assertTrue(EventResult.CANCEL.stopsPropagation());
        assertTrue(EventResult.SUCCESS.stopsPropagation());
        assertTrue(EventResult.CONSUME.stopsPropagation());
        assertFalse(EventResult.PASS.stopsPropagation());
    }

    @Test
    void testIsCancel() {
        assertTrue(EventResult.CANCEL.isCancel());
        assertFalse(EventResult.PASS.isCancel());
        assertFalse(EventResult.SUCCESS.isCancel());
        assertFalse(EventResult.CONSUME.isCancel());
    }
}
