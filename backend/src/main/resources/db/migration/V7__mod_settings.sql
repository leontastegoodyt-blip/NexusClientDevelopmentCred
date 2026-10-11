-- In-game Nexus settings (the mod's config/nexus/nexus.json), synced to the player's account so they
-- follow them between PCs. One row per player; saved_at is when the game saved them (milliseconds),
-- so an older copy never overwrites a newer one.

create table mod_settings (
    player_uuid  uuid primary key,
    data         text not null,                 -- the settings file (JSON), up to 256 KB
    saved_at     bigint not null,               -- when the game saved it (ms since 1970)
    updated_at   timestamp with time zone not null
);
