-- Mod Rerender: players can let others see and copy their setup ("workspace") – which mods, resource
-- packs and shaders an instance uses (Modrinth references only, never the files), its config text
-- files and game settings. Only stored while the player has sharing switched on.

create table workspaces (
    player_uuid  uuid primary key,
    data         text not null,                 -- the workspace (JSON), up to 2 MB
    updated_at   timestamp with time zone not null
);
