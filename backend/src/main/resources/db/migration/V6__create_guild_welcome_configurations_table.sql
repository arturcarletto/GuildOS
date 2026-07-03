CREATE TABLE guild_os.guild_welcome_configurations (
    id UUID PRIMARY KEY,
    registered_guild_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    channel_id VARCHAR(20) NOT NULL,
    message_template VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT guild_welcome_configurations_registered_guild_fk
        FOREIGN KEY (registered_guild_id) REFERENCES guild_os.guilds (id),
    CONSTRAINT guild_welcome_configurations_registered_guild_unique
        UNIQUE (registered_guild_id),
    CONSTRAINT guild_welcome_configurations_channel_id_valid
        CHECK (channel_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT guild_welcome_configurations_template_nonblank
        CHECK (char_length(btrim(message_template)) BETWEEN 1 AND 1000),
    CONSTRAINT guild_welcome_configurations_version_nonnegative CHECK (version >= 0)
);
