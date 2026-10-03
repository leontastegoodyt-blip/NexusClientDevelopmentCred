package com.nexusclient.launcher.errorscan;

/**
 * Diagnosis – one problem the Game Error Scan found, explained in plain words, with a fix.
 *
 * @param errorId     which known error it is (from known-errors.json), or "unknown"
 * @param title       a short headline, e.g. "Minecraft ran out of memory"
 * @param explanation what went wrong, in plain English
 * @param fix         what Nexus can do about it, e.g. "DISABLE_MOD" ("NONE" = nothing automatic)
 * @param fixArg      what the fix applies to, e.g. a mod id or a config file (may be null)
 * @param fixLabel    the fix, in plain words, e.g. "Switch off \"Sodium\""
 * @param evidence    the log line where the problem was spotted (for the curious)
 */
public record Diagnosis(String errorId, String title, String explanation, String fix, String fixArg,
                        String fixLabel, String evidence) {

    /** True if Nexus can fix this automatically. */
    public boolean canFix() {
        return fix != null && !fix.equals("NONE");
    }
}
