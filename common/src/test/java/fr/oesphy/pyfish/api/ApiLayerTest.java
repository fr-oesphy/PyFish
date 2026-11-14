package fr.oesphy.pyfish.api;

import fr.oesphy.pyfish.api.convert.TypeCoerce;
import fr.oesphy.pyfish.api.util.TickScheduler;
import fr.oesphy.pyfish.api.wrapper.EntityWrapper;
import fr.oesphy.pyfish.api.wrapper.IdentifierWrapper;
import fr.oesphy.pyfish.api.wrapper.LevelWrapper;
import fr.oesphy.pyfish.api.wrapper.NbtWrapper;
import fr.oesphy.pyfish.api.wrapper.PosWrapper;
import fr.oesphy.pyfish.api.wrapper.TextWrapper;
import fr.oesphy.pyfish.script.ClassFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pure-Java tests for the Phase 5 API layer.
 *
 * <p>Tests that require Minecraft bootstrap (registry initialisation) are guarded
 * with {@link #isMcBootstrapped()} and skipped if the game classes are not available.
 * No GraalPy {@code Context} or {@code Value} is used in this suite.
 */
class ApiLayerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isMcBootstrapped() {
        try {
            var ignored = net.minecraft.world.item.Items.AIR;
            return true;
        } catch (Exception | Error e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // TypeCoerce — ResourceLocation
    // -------------------------------------------------------------------------

    @Test
    void typeCoerceResourceLocation_withNamespace() {
        ResourceLocation rl = TypeCoerce.toResourceLocation("minecraft:stone");
        assertEquals("minecraft", rl.getNamespace());
        assertEquals("stone", rl.getPath());
    }

    @Test
    void typeCoerceResourceLocation_withoutNamespace() {
        ResourceLocation rl = TypeCoerce.toResourceLocation("stone");
        assertEquals("minecraft", rl.getNamespace());
        assertEquals("stone", rl.getPath());
    }

    // -------------------------------------------------------------------------
    // IdentifierWrapper
    // -------------------------------------------------------------------------

    @Test
    void identifierWrapperRoundTrip() {
        assertEquals("pyfish:ruby", IdentifierWrapper.of("pyfish:ruby").toString());
    }

    @Test
    void identifierWrapperNamespaceAndPath() {
        IdentifierWrapper w = IdentifierWrapper.of("pyfish:ruby");
        assertEquals("pyfish", w.namespace());
        assertEquals("ruby", w.path());
    }

    @Test
    void identifierWrapperEquality() {
        IdentifierWrapper a = IdentifierWrapper.of("pyfish:ruby");
        IdentifierWrapper b = IdentifierWrapper.of("pyfish:ruby");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // -------------------------------------------------------------------------
    // PosWrapper
    // -------------------------------------------------------------------------

    @Test
    void posWrapperXYZ() {
        PosWrapper p = PosWrapper.of(1, 2, 3);
        assertEquals(1, p.x());
        assertEquals(2, p.y());
        assertEquals(3, p.z());
    }

    @Test
    void posWrapperOffset() {
        PosWrapper p = PosWrapper.of(1, 2, 3);
        assertEquals(2, p.offset(1, 0, 0).x());
    }

    @Test
    void posWrapperAboveBelow() {
        PosWrapper p = PosWrapper.of(0, 64, 0);
        assertEquals(65, p.above().y());
        assertEquals(63, p.below().y());
    }

    @Test
    void posWrapperToString() {
        assertEquals("(1, 2, 3)", PosWrapper.of(1, 2, 3).toString());
    }

    // -------------------------------------------------------------------------
    // NbtWrapper
    // -------------------------------------------------------------------------

    @Test
    void nbtWrapperEmpty() {
        assertTrue(NbtWrapper.empty().isEmpty());
    }

    @Test
    void nbtWrapperPutAndGet() {
        NbtWrapper wrapper = NbtWrapper.empty();
        // Write via CompoundTag directly (no GraalPy Value available in tests)
        wrapper.unwrap().putString("x", "hello");
        Object result = wrapper.getMember("x");
        assertEquals("hello", result);
    }

    @Test
    void nbtWrapperIntRoundtrip() {
        NbtWrapper wrapper = NbtWrapper.empty();
        wrapper.unwrap().putInt("count", 42);
        assertEquals(42, wrapper.getMember("count"));
    }

    @Test
    void nbtWrapperKeys() {
        NbtWrapper wrapper = NbtWrapper.empty();
        wrapper.unwrap().putString("alpha", "a");
        wrapper.unwrap().putString("beta", "b");
        assertTrue(wrapper.keys().contains("alpha"));
        assertTrue(wrapper.keys().contains("beta"));
    }

    @Test
    void nbtWrapperHasMember() {
        NbtWrapper wrapper = NbtWrapper.empty();
        assertFalse(wrapper.hasMember("missing"));
        wrapper.unwrap().putString("present", "yes");
        assertTrue(wrapper.hasMember("present"));
    }

    @Test
    void nbtWrapperNestedCompoundTag() {
        NbtWrapper wrapper = NbtWrapper.empty();
        CompoundTag inner = new CompoundTag();
        inner.putInt("value", 99);
        wrapper.unwrap().put("nested", inner);
        Object member = wrapper.getMember("nested");
        assertInstanceOf(NbtWrapper.class, member);
        assertEquals(99, ((NbtWrapper) member).getMember("value"));
    }

    // -------------------------------------------------------------------------
    // TextWrapper
    // -------------------------------------------------------------------------

    @Test
    void textWrapperLiteral() {
        TextWrapper tw = TextWrapper.literal("Hello, world!");
        assertEquals("Hello, world!", tw.string());
        assertEquals("Hello, world!", tw.toString());
    }

    @Test
    void textWrapperTranslatable() {
        TextWrapper tw = TextWrapper.translatable("item.minecraft.stone");
        assertNotNull(tw);
        assertNotNull(tw.unwrap());
    }

    // -------------------------------------------------------------------------
    // EntityWrapper / LevelWrapper null guards
    // -------------------------------------------------------------------------

    @Test
    void entityWrapperOfNullThrows() {
        assertThrows(NullPointerException.class, () -> EntityWrapper.of(null));
    }

    @Test
    void levelWrapperOfNullThrows() {
        assertThrows(NullPointerException.class, () -> LevelWrapper.of(null));
    }

    // -------------------------------------------------------------------------
    // TickScheduler
    // -------------------------------------------------------------------------

    private TickScheduler scheduler;

    @BeforeEach
    void freshScheduler() {
        scheduler = new TickScheduler();
    }

    @Test
    void tickSchedulerScheduleAndTick() {
        AtomicBoolean called = new AtomicBoolean(false);
        scheduler.schedule(() -> called.set(true), 2);

        scheduler.tick(); // tick 1 — not yet
        assertFalse(called.get());
        scheduler.tick(); // tick 2 — fires
        assertTrue(called.get());
    }

    @Test
    void tickSchedulerCancel() {
        AtomicBoolean called = new AtomicBoolean(false);
        long id = scheduler.schedule(() -> called.set(true), 1);
        scheduler.cancel(id);
        scheduler.tick();
        assertFalse(called.get());
    }

    @Test
    void tickSchedulerMultipleTasks() {
        AtomicInteger order = new AtomicInteger(0);
        AtomicInteger firstAt = new AtomicInteger(-1);
        AtomicInteger secondAt = new AtomicInteger(-1);

        scheduler.schedule(() -> firstAt.set(order.incrementAndGet()), 1);
        scheduler.schedule(() -> secondAt.set(order.incrementAndGet()), 2);

        scheduler.tick(); // tick 1 — first fires
        scheduler.tick(); // tick 2 — second fires

        assertEquals(1, firstAt.get());
        assertEquals(2, secondAt.get());
    }

    @Test
    void tickSchedulerPendingCount() {
        scheduler.schedule(() -> {}, 5);
        scheduler.schedule(() -> {}, 10);
        assertEquals(2, scheduler.pendingCount());
        scheduler.tick(); // no tasks due yet
        assertEquals(2, scheduler.pendingCount());
    }

    @Test
    void tickSchedulerCurrentTick() {
        assertEquals(0, scheduler.currentTick());
        scheduler.tick();
        assertEquals(1, scheduler.currentTick());
        scheduler.tick();
        assertEquals(2, scheduler.currentTick());
    }

    // -------------------------------------------------------------------------
    // ClassFilter
    // -------------------------------------------------------------------------

    @Test
    void classFilterDefaultDenied() {
        assertFalse(ClassFilter.DEFAULT.isAllowed("java.lang.Runtime"));
        assertFalse(ClassFilter.DEFAULT.isAllowed("java.lang.ProcessBuilder"));
        assertFalse(ClassFilter.DEFAULT.isAllowed("java.lang.reflect.Method"));
        assertFalse(ClassFilter.DEFAULT.isAllowed("sun.misc.Unsafe"));
        assertFalse(ClassFilter.DEFAULT.isAllowed("jdk.internal.misc.VM"));
        assertFalse(ClassFilter.DEFAULT.isAllowed("com.sun.proxy.Proxy"));
    }

    @Test
    void classFilterAllowsSafeClasses() {
        assertTrue(ClassFilter.DEFAULT.isAllowed("net.minecraft.world.level.Level"));
        assertTrue(ClassFilter.DEFAULT.isAllowed("java.util.ArrayList"));
        assertTrue(ClassFilter.DEFAULT.isAllowed("java.lang.String"));
    }

    @Test
    void classFilterDenyCustom() {
        ClassFilter filter = new ClassFilter();
        filter.deny("my.dangerous.");
        assertFalse(filter.isAllowed("my.dangerous.Class"));
        assertFalse(filter.isAllowed("my.dangerous.package.Foo"));
        // Other classes still allowed
        assertTrue(filter.isAllowed("my.safe.Class"));
    }

    @Test
    void classFilterAllowOverridesDefault() {
        ClassFilter filter = new ClassFilter();
        filter.allow("java.lang.String");
        // Allow-list is now non-empty → only "java.lang.String" passes (unless denied)
        assertTrue(filter.isAllowed("java.lang.String"));
        assertFalse(filter.isAllowed("java.lang.Object")); // not in allow-list
    }

    @Test
    void classFilterReset() {
        ClassFilter filter = new ClassFilter();
        filter.allow("some.Class");
        filter.deny("extra.Prefix");
        filter.reset();
        // After reset: allow-list empty, default deny-list restored
        assertTrue(filter.isAllowed("java.util.ArrayList"));
        assertFalse(filter.isAllowed("java.lang.Runtime"));
        // extra.Prefix no longer denied
        assertTrue(filter.isAllowed("extra.PrefixClass"));
    }
}
