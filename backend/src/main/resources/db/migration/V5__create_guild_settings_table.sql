CREATE TABLE guild_os.guild_settings (
    id UUID PRIMARY KEY,
    registered_guild_id UUID NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    locale_tag VARCHAR(35) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT guild_settings_registered_guild_fk
        FOREIGN KEY (registered_guild_id) REFERENCES guild_os.guilds (id),
    CONSTRAINT guild_settings_registered_guild_unique UNIQUE (registered_guild_id),
    CONSTRAINT guild_settings_timezone_nonblank
        CHECK (char_length(btrim(timezone)) BETWEEN 1 AND 64),
    CONSTRAINT guild_settings_locale_tag_nonblank
        CHECK (char_length(btrim(locale_tag)) BETWEEN 1 AND 35),
    CONSTRAINT guild_settings_version_nonnegative CHECK (version >= 0)
);
