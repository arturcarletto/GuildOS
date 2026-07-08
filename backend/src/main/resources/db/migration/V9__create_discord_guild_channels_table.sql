CREATE TABLE guild_os.discord_guild_channels (
    id UUID PRIMARY KEY,
    discord_guild_id VARCHAR(32) NOT NULL,
    discord_channel_id VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    position INTEGER,
    active BOOLEAN NOT NULL,
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT discord_guild_channels_guild_channel_unique
        UNIQUE (discord_guild_id, discord_channel_id),
    CONSTRAINT discord_guild_channels_guild_fk
        FOREIGN KEY (discord_guild_id) REFERENCES guild_os.guilds (discord_guild_id),
    CONSTRAINT discord_guild_channels_guild_snowflake_check
        CHECK (discord_guild_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT discord_guild_channels_channel_snowflake_check
        CHECK (discord_channel_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT discord_guild_channels_name_nonblank
        CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT discord_guild_channels_type_nonblank
        CHECK (char_length(btrim(type)) BETWEEN 1 AND 32)
);

CREATE INDEX discord_guild_channels_active_list_idx
    ON guild_os.discord_guild_channels (discord_guild_id, active, position, name, discord_channel_id);
