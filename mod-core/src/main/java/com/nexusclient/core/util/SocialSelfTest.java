package com.nexusclient.core.util;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.online.Social;

/**
 * The Nexus Social part of the developer self-tests (only runs when the game has a Nexus sign-in,
 * e.g. against a test server): invites the first online friend to a party, sends "@p" party chat
 * through the chat hook, presses the party ping key, and logs what happened. Players never see it.
 */
public final class SocialSelfTest {

    private static String shortCode;

    private SocialSelfTest() {}

    /** True if this self-test should run (the game was given a Nexus sign-in). */
    public static boolean wanted() {
        return System.getenv("NEXUS_TOKEN") != null;
    }

    /** Called with ticks counted from the start of this part. Returns true when it's finished. */
    public static boolean step(int tick) {
        Social social = NexusCore.get().get(Social.class);
        if (tick == 0) {
            log(social.status() + ", friends " + social.friends().size());
            for (Social.Person friend : social.friends()) {
                if (friend.online) {
                    log("inviting " + friend.name);
                    social.inviteToParty(friend);
                    break;
                }
            }
        } else if (tick == 20) {
            // Short share codes: make one for the crosshair through the Nexus server…
            com.nexusclient.core.ShareCodes.copy("CROSSHAIR",
                    com.nexusclient.core.ShareCodes.crosshairCode(NexusCore.get().module("crosshair")),
                    new com.nexusclient.core.ShareCodes.Done() {
                        public void message(String text) {
                            log("share code: " + text);
                            if (text.startsWith("Copied NX-")) {
                                shortCode = text.substring(7, text.length() - 1);
                            }
                        }
                    });
        } else if (tick == 50 && shortCode != null) {
            // …and use it again (looked up on the server).
            com.nexusclient.core.ShareCodes.use(shortCode, new com.nexusclient.core.ShareCodes.Done() {
                public void message(String text) {
                    log("using " + shortCode + ": " + text);
                }
            });
        } else if (tick == 64) {
            log("party of " + social.partyMembers().size() + ", " + social.partyChat().size() + " party messages");
            log("chat typed @p → handled = " + NexusCore.get().chatInput("@p hi from the game"));
        } else if (tick == 74) {
            NexusCore.get().partyPingKey();
        } else if (tick == 110) {
            log("party messages " + social.partyChat().size() + ", clan " + social.clanTag());
            return true;
        }
        return false;
    }

    private static void log(String message) {
        System.out.println("[Nexus] self-test social: " + message);
    }
}
