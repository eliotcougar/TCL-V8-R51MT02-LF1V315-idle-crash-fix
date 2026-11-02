package com.eliotcougar.tvidlefix;

/** Invalidates delayed work as soon as an idle session ends or changes. */
final class IdleSession {
    private boolean dreaming;
    private boolean screenOff;
    private int generation;

    int enterDream() {
        if (dreaming) return -1;
        dreaming = true;
        return ++generation;
    }

    int enterScreenOff() {
        if (screenOff) return -1;
        screenOff = true;
        return ++generation;
    }

    int exitDream(boolean interactive) {
        if (interactive) {
            wake();
            return -1;
        }
        if (!dreaming && screenOff) return -1;
        dreaming = false;
        screenOff = true;
        return ++generation;
    }

    void wake() {
        dreaming = false;
        screenOff = false;
        ++generation;
    }

    boolean accepts(int token) {
        return token == generation && (dreaming || screenOff);
    }

    int generation() { return generation; }
    String mode() { return screenOff ? "screen off" : dreaming ? "screensaver" : "awake"; }
}
