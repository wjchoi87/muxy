package com.termux.terminal;

/**
 * Stripped-down client interface for the vendored Termux terminal emulator.
 * The original references com.termux.terminal.TerminalSession (which we don't
 * use because the PTY runs on the Mac, not on-device). Only the log methods
 * and onTerminalCursorStateChange() / getTerminalCursorStyle() are referenced
 * by TerminalEmulator.java; keep those signatures unchanged. We pass `null`
 * everywhere in our code, so this interface is effectively dead at runtime.
 */
public interface TerminalSessionClient {
    void onTerminalCursorStateChange(boolean state);

    Integer getTerminalCursorStyle();

    void logError(String tag, String message);
    void logWarn(String tag, String message);
    void logInfo(String tag, String message);
    void logDebug(String tag, String message);
    void logVerbose(String tag, String message);
    void logStackTraceWithMessage(String tag, String message, Exception e);
    void logStackTrace(String tag, Exception e);
}
