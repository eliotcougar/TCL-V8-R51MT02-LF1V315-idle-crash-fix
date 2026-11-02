package com.eliotcougar.tvidlefix;

public final class IdleSessionTest {
    public static void main(String[] args) {
        IdleSession state = new IdleSession();
        check(!state.accepts(0), "No work before an idle event");
        int first = state.enterDream();
        check(state.accepts(first), "Dream starts a valid session");
        check(state.enterDream() == -1 && state.accepts(first), "Duplicates cannot create endless retry bursts");
        state.wake();
        check(!state.accepts(first), "Wake rejects an already-queued callback");
        int second = state.enterDream();
        check(!state.accepts(first) && state.accepts(second), "Old callback cannot leak into a later session");
        int off = state.enterScreenOff();
        check(!state.accepts(second) && state.accepts(off), "Screen-off replaces earlier dream work");
        check(state.enterScreenOff() == -1, "Duplicate screen-off is bounded");
        state.wake();
        check(!state.accepts(off) && state.mode().equals("awake"), "Wake cancels both idle modes");
        int dream = state.enterDream();
        int continuedIdle = state.exitDream(false);
        check(!state.accepts(dream) && state.accepts(continuedIdle), "Dream stopping during standby keeps idle protection");
        check(state.mode().equals("screen off"), "Dream stopped is not necessarily a wake event");
        check(state.exitDream(false) == -1 && state.accepts(continuedIdle), "Duplicate dream-stop cannot restart standby bursts forever");
        state.exitDream(true);
        check(!state.accepts(continuedIdle), "Interactive dream exit cancels standby retries");
        System.out.println("PASS: idle transitions, duplicate events and stale-callback cancellation");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
