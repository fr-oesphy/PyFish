package fr.oesphy.pyfish.event;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Phase 4 event system.
 *
 * <p>Tests that require a live GraalPy context (to create executable {@link Value}
 * instances) are guarded by {@code assumeTrue(isGraalPyVfsAvailable())} so they
 * skip gracefully in environments where the GraalPy VFS is absent (e.g. CI
 * without the Python embedding jar).
 *
 * <p>All other tests ({@link EventResult}, {@link PyEvent}, structural
 * {@link EventGroup} / {@link EventGroups} / {@link EventFacade} tests) are
 * pure-Java and always run.
 */
class EventSystemTest {

    // =========================================================================
    // EventResult tests
    // =========================================================================

    @Test
    void eventResultFromString_cancel() {
        assertEquals(EventResult.CANCEL, EventResult.fromString("cancel"));
        assertEquals(EventResult.CANCEL, EventResult.fromString("CANCEL"));
        assertEquals(EventResult.CANCEL, EventResult.fromString("  Cancel  "));
    }

    @Test
    void eventResultFromString_success() {
        assertEquals(EventResult.SUCCESS, EventResult.fromString("success"));
        assertEquals(EventResult.SUCCESS, EventResult.fromString("SUCCESS"));
    }

    @Test
    void eventResultFromString_consume() {
        assertEquals(EventResult.CONSUME, EventResult.fromString("consume"));
    }

    @Test
    void eventResultFromString_pass() {
        assertEquals(EventResult.PASS, EventResult.fromString("pass"));
    }

    @Test
    void eventResultFromString_null_and_unknown() {
        assertNull(EventResult.fromString(null));
        assertNull(EventResult.fromString(""));
        assertNull(EventResult.fromString("unknown_value"));
        assertNull(EventResult.fromString("true"));
    }

    @Test
    void eventResultIsCancelled_cancelAndConsume() {
        assertTrue(EventResult.CANCEL.isCancelled());
        assertTrue(EventResult.CONSUME.isCancelled());
    }

    @Test
    void eventResultIsCancelled_passAndSuccess() {
        assertFalse(EventResult.PASS.isCancelled());
        assertFalse(EventResult.SUCCESS.isCancelled());
    }

    // =========================================================================
    // PyEvent tests
    // =========================================================================

    @Test
    void pyEventDefaultsToPass() {
        PyEvent event = new PyEvent();
        assertEquals(EventResult.PASS, event.result());
    }

    @Test
    void pyEventIsNotCancellableByDefault() {
        PyEvent event = new PyEvent();
        assertFalse(event.isCancellable());
    }

    @Test
    void pyEventCancellableConstructor() {
        PyEvent event = new PyEvent(true);
        assertTrue(event.isCancellable());
    }

    @Test
    void pyEventCancel() {
        PyEvent event = new PyEvent(true);
        event.cancel();
        assertEquals(EventResult.CANCEL, event.result());
        assertTrue(event.isCancelled());
    }

    @Test
    void pyEventCancelThrowsIfNotCancellable() {
        PyEvent event = new PyEvent(false);
        assertThrows(UnsupportedOperationException.class, event::cancel);
        // Result should still be PASS
        assertEquals(EventResult.PASS, event.result());
    }

    @Test
    void pyEventSucceed() {
        PyEvent event = new PyEvent();
        event.succeed();
        assertEquals(EventResult.SUCCESS, event.result());
        assertFalse(event.isCancelled());
    }

    @Test
    void pyEventConsume() {
        PyEvent event = new PyEvent();
        event.consume();
        assertEquals(EventResult.CONSUME, event.result());
        assertTrue(event.isCancelled());
    }

    @Test
    void pyEventSetResult() {
        PyEvent event = new PyEvent();
        event.setResult(EventResult.CANCEL);
        assertEquals(EventResult.CANCEL, event.result());
    }

    @Test
    void pyEventSetResult_nullThrows() {
        PyEvent event = new PyEvent();
        assertThrows(NullPointerException.class, () -> event.setResult(null));
    }

    @Test
    void pyEventToStringContainsState() {
        PyEvent event = new PyEvent(true);
        String s = event.toString();
        assertTrue(s.contains("cancellable=true"));
        assertTrue(s.contains("PASS"));
    }

    // =========================================================================
    // EventHandler pure-Java tests (no GraalPy context needed)
    // =========================================================================

    @Test
    void eventHandlerInitialState() {
        EventHandler handler = new EventHandler("player", "chat");
        assertEquals("player", handler.groupName());
        assertEquals("chat", handler.eventName());
        assertEquals(0, handler.listenerCount());
    }

    @Test
    void eventHandlerFireWithNoListeners_returnsPass() {
        EventHandler handler = new EventHandler("player", "chat");
        PyEvent event = new PyEvent(true);
        EventResult result = handler.fire(event);
        assertEquals(EventResult.PASS, result);
        assertEquals(0, handler.listenerCount());
    }

    @Test
    void eventHandlerFireWithNoListeners_withSubject_returnsPass() {
        EventHandler handler = new EventHandler("block", "right_clicked");
        PyEvent event = new PyEvent();
        EventResult result = handler.fire(event, "minecraft:lever");
        assertEquals(EventResult.PASS, result);
    }

    @Test
    void eventHandlerClearListeners_emptyIsIdempotent() {
        EventHandler handler = new EventHandler("server", "tick");
        // Should not throw even when already empty
        assertDoesNotThrow(handler::clearListeners);
        assertEquals(0, handler.listenerCount());
    }

    // =========================================================================
    // EventGroup tests
    // =========================================================================

    @Test
    void eventGroupCreatesHandlersDynamically() {
        EventGroup group = new EventGroup("player");
        EventHandler handler = group.getHandler("chat");
        assertNotNull(handler);
        // Same instance on second access
        assertSame(handler, group.getHandler("chat"));
    }

    @Test
    void eventGroupHasMemberAlwaysTrue() {
        EventGroup group = new EventGroup("player");
        assertTrue(group.hasMember("chat"));
        assertTrue(group.hasMember("some_new_event_that_does_not_exist_yet"));
    }

    @Test
    void eventGroupGetMemberReturnsHandler() {
        EventGroup group = new EventGroup("block");
        Object member = group.getMember("right_clicked");
        assertInstanceOf(EventHandler.class, member);
    }

    @Test
    void eventGroupGetMemberKeys() {
        EventGroup group = new EventGroup("entity");
        // Access two handlers to populate the map
        group.getHandler("spawn");
        group.getHandler("death");
        Object keys = group.getMemberKeys();
        assertNotNull(keys);
    }

    @Test
    void eventGroupPutMemberIsNoOp() {
        EventGroup group = new EventGroup("item");
        // Calling putMember should not throw
        assertDoesNotThrow(() -> group.putMember("use", null));
    }

    @Test
    void eventGroupFireWithNoHandlers_returnsPass() {
        EventGroup group = new EventGroup("level");
        PyEvent event = new PyEvent();
        // "tick" handler not yet created → should return PASS
        assertEquals(EventResult.PASS, group.fire("tick", event));
    }

    @Test
    void eventGroupFireAfterHandlerAccessed_returnsPass_whenNoListeners() {
        EventGroup group = new EventGroup("level");
        group.getHandler("tick"); // creates handler with no listeners
        PyEvent event = new PyEvent();
        assertEquals(EventResult.PASS, group.fire("tick", event));
    }

    @Test
    void eventGroupClearAllListeners() {
        // We can only test indirectly (count-based) since Value requires GraalPy
        EventGroup group = new EventGroup("player");
        EventHandler h = group.getHandler("chat");
        // Clear while empty — should not throw
        assertDoesNotThrow(group::clearAllListeners);
        assertEquals(0, h.listenerCount());
    }

    @Test
    void eventGroupOnAliasEqualsGetHandler() {
        EventGroup group = new EventGroup("server");
        assertSame(group.getHandler("start"), group.on("start"));
    }

    @Test
    void eventGroupName() {
        EventGroup group = new EventGroup("recipe");
        assertEquals("recipe", group.name());
    }

    // =========================================================================
    // EventGroups tests
    // =========================================================================

    @Test
    void eventGroupsGetReturnsCorrectGroup() {
        assertSame(EventGroups.PLAYER,  EventGroups.get("player"));
        assertSame(EventGroups.BLOCK,   EventGroups.get("block"));
        assertSame(EventGroups.ENTITY,  EventGroups.get("entity"));
        assertSame(EventGroups.ITEM,    EventGroups.get("item"));
        assertSame(EventGroups.LEVEL,   EventGroups.get("level"));
        assertSame(EventGroups.RECIPE,  EventGroups.get("recipe"));
        assertSame(EventGroups.TAG,     EventGroups.get("tag"));
        assertSame(EventGroups.SERVER,  EventGroups.get("server"));
        assertSame(EventGroups.STARTUP, EventGroups.get("startup"));
    }

    @Test
    void eventGroupsGetUnknown_returnsNull() {
        assertNull(EventGroups.get("nonexistent"));
        assertNull(EventGroups.get(""));
    }

    @Test
    void eventGroupsAllContainsAllStandard() {
        assertEquals(9, EventGroups.ALL.size());
        assertTrue(EventGroups.ALL.containsKey("player"));
        assertTrue(EventGroups.ALL.containsKey("startup"));
    }

    @Test
    void eventGroupsClearAll_doesNotThrow() {
        assertDoesNotThrow(EventGroups::clearAll);
    }

    @Test
    void eventGroupsClearServerEvents_doesNotThrow() {
        assertDoesNotThrow(EventGroups::clearServerEvents);
    }

    @Test
    void eventGroupsClearServerEvents_doesNotClearStartup() {
        // Access a startup handler
        EventGroups.STARTUP.getHandler("register_items");
        // clearServerEvents must NOT throw, and startup handler still exists
        EventGroups.clearServerEvents();
        // Handler object itself still there (just listeners cleared)
        assertNotNull(EventGroups.STARTUP.getHandler("register_items"));
    }

    // =========================================================================
    // EventFacade tests
    // =========================================================================

    @Test
    void eventFacadeHasExpectedGroups() {
        EventFacade f = EventFacade.INSTANCE;
        assertNotNull(f.player);
        assertNotNull(f.block);
        assertNotNull(f.entity);
        assertNotNull(f.item);
        assertNotNull(f.level);
        assertNotNull(f.recipe);
        assertNotNull(f.tag);
        assertNotNull(f.server);
        assertNotNull(f.startup);
    }

    @Test
    void eventFacadeGroupsSameAsEventGroupsSingletons() {
        EventFacade f = EventFacade.INSTANCE;
        assertSame(EventGroups.PLAYER,  f.player);
        assertSame(EventGroups.BLOCK,   f.block);
        assertSame(EventGroups.ENTITY,  f.entity);
        assertSame(EventGroups.ITEM,    f.item);
        assertSame(EventGroups.LEVEL,   f.level);
        assertSame(EventGroups.RECIPE,  f.recipe);
        assertSame(EventGroups.TAG,     f.tag);
        assertSame(EventGroups.SERVER,  f.server);
        assertSame(EventGroups.STARTUP, f.startup);
    }

    @Test
    void eventFacadeInstanceIsSingleton() {
        assertSame(EventFacade.INSTANCE, EventFacade.INSTANCE);
    }

    // =========================================================================
    // GraalPy-dependent tests (require VFS — skip if unavailable)
    // =========================================================================

    /**
     * Checks whether a non-empty GraalPy VFS is available on the classpath.
     * Mirrors the check used in {@link fr.oesphy.pyfish.script.PyContextFactory}.
     */
    private static boolean isGraalPyVfsAvailable() {
        try (var is = EventSystemTest.class.getClassLoader()
                .getResourceAsStream("org.graalvm.python.vfs/fileslist.txt")) {
            return is != null && is.read() != -1;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void eventHandlerRegisterAndFireWithRealValue() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not available — skipping GraalPy-dependent test");

        // Build a minimal Python context
        try (Context ctx = Context.newBuilder("python")
                .allowAllAccess(true)
                .build()) {

            ctx.initialize("python");

            // Create a Python callable that cancels the event
            Value pyFunc = ctx.eval("python",
                    "def _handler(e): e.cancel()\n_handler");

            EventHandler handler = new EventHandler("player", "chat");
            handler.register(pyFunc);
            assertEquals(1, handler.listenerCount());

            PyEvent event = new PyEvent(true);
            EventResult result = handler.fire(event);
            // The Python callable called e.cancel(), so result should be CANCEL
            assertEquals(EventResult.CANCEL, result);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    void eventHandlerFilterSkipsNonMatching() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not available — skipping GraalPy-dependent test");

        try (Context ctx = Context.newBuilder("python").allowAllAccess(true).build()) {
            ctx.initialize("python");

            // A handler that cancels — but registered with filter "minecraft:stone"
            Value pyFunc = ctx.eval("python", "def _h(e): e.cancel()\n_h");

            EventHandler handler = new EventHandler("block", "right_clicked");
            handler.register(pyFunc, "minecraft:stone");

            PyEvent event = new PyEvent(true);
            // Fire with a different subject — listener should be skipped
            handler.fire(event, "minecraft:dirt");
            assertEquals(EventResult.PASS, event.result()); // not cancelled
        }
    }

    @Test
    void eventHandlerFilterMatchesSame() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not available — skipping GraalPy-dependent test");

        try (Context ctx = Context.newBuilder("python").allowAllAccess(true).build()) {
            ctx.initialize("python");

            Value pyFunc = ctx.eval("python", "def _h(e): e.cancel()\n_h");

            EventHandler handler = new EventHandler("block", "right_clicked");
            handler.register(pyFunc, "minecraft:stone");

            PyEvent event = new PyEvent(true);
            handler.fire(event, "minecraft:stone");
            assertEquals(EventResult.CANCEL, event.result()); // cancelled
        }
    }

    @Test
    void eventHandlerClearListeners() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not available — skipping GraalPy-dependent test");

        try (Context ctx = Context.newBuilder("python").allowAllAccess(true).build()) {
            ctx.initialize("python");

            Value pyFunc = ctx.eval("python", "def _h(e): e.cancel()\n_h");

            EventHandler handler = new EventHandler("server", "tick");
            handler.register(pyFunc);
            assertEquals(1, handler.listenerCount());

            handler.clearListeners();
            assertEquals(0, handler.listenerCount());

            // Firing after clear should not cancel
            PyEvent event = new PyEvent(true);
            handler.fire(event);
            assertEquals(EventResult.PASS, event.result());
        }
    }

    @Test
    void eventHandlerFireCatchesExceptions() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not available — skipping GraalPy-dependent test");

        try (Context ctx = Context.newBuilder("python").allowAllAccess(true).build()) {
            ctx.initialize("python");

            // A Python callable that raises an exception
            Value pyFunc = ctx.eval("python",
                    "def _h(e): raise ValueError('test error')\n_h");

            EventHandler handler = new EventHandler("player", "chat");
            handler.register(pyFunc);

            PyEvent event = new PyEvent();
            // fire() must NOT throw, even though the listener throws
            assertDoesNotThrow(() -> handler.fire(event));
            // Result stays PASS (listener threw before modifying it)
            assertEquals(EventResult.PASS, event.result());
        }
    }

    @Test
    void eventHandlerStringReturnValueCoercedToResult() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not available — skipping GraalPy-dependent test");

        try (Context ctx = Context.newBuilder("python").allowAllAccess(true).build()) {
            ctx.initialize("python");

            // Handler returns "cancel" string instead of calling e.cancel()
            Value pyFunc = ctx.eval("python",
                    "def _h(e): return 'cancel'\n_h");

            EventHandler handler = new EventHandler("item", "use");
            // Note: event is NOT cancellable, but string return coercion calls setResult() not cancel()
            handler.register(pyFunc);

            PyEvent event = new PyEvent(false);
            EventResult result = handler.fire(event);
            assertEquals(EventResult.CANCEL, result);
        }
    }
}
