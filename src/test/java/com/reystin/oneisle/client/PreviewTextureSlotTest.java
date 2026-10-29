package com.reystin.oneisle.client;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PreviewTextureSlotTest {

    @Test
    @DisplayName("9.6 Preview texture lifecycle: 50 open/close cycles register and release exactly once")
    void fiftyOpenCloseCycles() {
        PreviewTextureSlot slot = new PreviewTextureSlot();
        AtomicInteger created = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();

        for (int i = 0; i < 50; i++) {
            slot.ensure(192, () -> released.incrementAndGet(), () -> created.incrementAndGet());
            assertTrue(slot.active());
            assertEquals(192, slot.resolution());
            slot.release(() -> released.incrementAndGet());
            assertFalse(slot.active());
        }

        assertEquals(50, created.get(), "One texture per editor session");
        assertEquals(50, released.get(), "Every registration must be released");
    }

    @Test
    @DisplayName("9.6 Preview texture lifecycle: same resolution reuses, resolution change swaps")
    void resolutionSwap() {
        PreviewTextureSlot slot = new PreviewTextureSlot();
        AtomicInteger created = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        Runnable onRelease = () -> released.incrementAndGet();
        Runnable onCreate = () -> created.incrementAndGet();

        slot.ensure(192, onRelease, onCreate);
        slot.ensure(192, onRelease, onCreate);
        assertEquals(1, created.get());
        assertEquals(0, released.get());

        slot.ensure(256, onRelease, onCreate);
        assertEquals(2, created.get());
        assertEquals(1, released.get());
        assertEquals(256, slot.resolution());

        slot.ensure(256, onRelease, onCreate);
        assertEquals(2, created.get());

        slot.release(onRelease);
        slot.release(onRelease);
        assertEquals(2, released.get(), "Double release must be a no-op");
        assertFalse(slot.active());
    }
}
