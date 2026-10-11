-- Screenshots uploaded by the Screenshot Uploader mod (press F2 in game → link copied).
-- The picture itself is stored here; it's shown at /s/<id>.png (public, so links can be shared).

create table screenshots (
    id           varchar(12) primary key,      -- random, e.g. "k7Qp2xZa9mRt" (hard to guess)
    player_uuid  uuid not null,
    image        bytea not null,                -- the PNG file
    created_at   timestamp with time zone not null
);
create index screenshots_player on screenshots (player_uuid, created_at);
