package com.muxy.app.ui.terminal

/** Mirrors iOS TerminalModifier — single sticky modifier slot. */
enum class AccessoryModifier(val title: String, val displayName: String, val glyph: String) {
    CTRL("ctrl", "Control", "⌃"),
    SHIFT("shift", "Shift", "⇧"),
    ALT("alt", "Option", "⌥"),
    CMD("cmd", "Command", "⌘"),
}
