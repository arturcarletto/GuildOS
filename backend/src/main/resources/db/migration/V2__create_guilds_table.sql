CREATE TABLE guild_os.guilds (
    id UUID PRIMARY KEY,
    discord_guild_id VARCHAR(32) NOT NULL,
    guild_name VARCHAR(100) NOT NULL,
    connection_status VARCHAR(16) NOT NULL,
    first_connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    disconnected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT guilds_discord_guild_id_unique UNIQUE (discord_guild_id),
    CONSTRAINT guilds_discord_guild_id_length
        CHECK (char_length(discord_guild_id) BETWEEN 1 AND 32),
    CONSTRAINT guilds_guild_name_length
        CHECK (char_length(btrim(guild_name)) BETWEEN 1 AND 100),
    CONSTRAINT guilds_connection_status
        CHECK (connection_status IN ('CONNECTED', 'DISCONNECTED')),
    CONSTRAINT guilds_disconnected_at_status
        CHECK (
            (connection_status = 'CONNECTED' AND disconnected_at IS NULL)
            OR (connection_status = 'DISCONNECTED' AND disconnected_at IS NOT NULL)
        )
);
