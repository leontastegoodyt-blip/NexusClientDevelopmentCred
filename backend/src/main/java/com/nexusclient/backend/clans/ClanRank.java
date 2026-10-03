package com.nexusclient.backend.clans;

/**
 * ClanRank – the clan ranks from the project brief, highest first, with how many of each a clan may have.
 * What each rank may do (defaults chosen for the open questions, see the project brief step 14):
 *   OWNER        everything (only one; can delete the clan and hand it to someone else)
 *   CO_OWNER     invite, kick and change ranks of lower ranks, edit clan settings, start polls
 *   FIRST_CLASS  the same (they can never touch the owner or co-owner)
 *   VIP          edit clan settings and start polls, but not add or kick players
 *   MEMBER       clan chat, vote, and normal features
 * Nobody can kick or change the rank of someone with the same or a higher rank.
 */
public enum ClanRank {
    OWNER(1), CO_OWNER(1), FIRST_CLASS(2), VIP(10), MEMBER(Integer.MAX_VALUE);

    /** How many members may hold this rank at once. */
    public final int limit;

    ClanRank(int limit) {
        this.limit = limit;
    }

    public boolean isAbove(ClanRank other) {
        return ordinal() < other.ordinal();
    }

    public boolean canInviteAndKick() {
        return this == OWNER || this == CO_OWNER || this == FIRST_CLASS;
    }

    public boolean canSetRanks() {
        return canInviteAndKick();
    }

    public boolean canEditSettings() {
        return this != MEMBER;
    }

    public boolean canStartPolls() {
        return this != MEMBER;
    }
}
