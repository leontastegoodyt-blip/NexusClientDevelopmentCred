-- Cosmetics (build step 17): capes, wings, hats and emotes, only visible to other Nexus players.
-- Pictures are served from src/main/resources/static/cosmetics/<id>.png.
-- price_cents is empty (null) for free items. Pricing is undecided, so everything is free for now;
-- anything paid must follow Minecraft's usage guidelines.

create table cosmetics (
    id           varchar(32) primary key,
    type         varchar(16) not null,          -- CAPE, WINGS, HAT, EMOTE
    name         varchar(48) not null,
    price_cents  int,
    created_at   timestamp with time zone not null
);

-- What each player is wearing: one item per type.
create table cosmetic_equipped (
    player_uuid  uuid not null references players (uuid) on delete cascade,
    type         varchar(16) not null,
    cosmetic_id  varchar(32) not null references cosmetics (id) on delete cascade,
    primary key (player_uuid, type)
);

insert into cosmetics (id, type, name, price_cents, created_at) values
    ('neon_cyan_cape',    'CAPE',  'Neon Cyan Cape',    null, current_timestamp),
    ('neon_magenta_cape', 'CAPE',  'Neon Magenta Cape', null, current_timestamp),
    ('nexus_cape',        'CAPE',  'Nexus Cape',        null, current_timestamp),
    ('neon_wings',        'WINGS', 'Neon Wings',        null, current_timestamp),
    ('halo',              'HAT',   'Halo',              null, current_timestamp),
    ('wave',              'EMOTE', 'Wave',              null, current_timestamp);
