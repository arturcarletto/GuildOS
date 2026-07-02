CREATE TABLE guild_os.operator_accounts (
    id UUID PRIMARY KEY,
    discord_user_id VARCHAR(32) NOT NULL,
    discord_username VARCHAR(100) NOT NULL,
    discord_global_display_name VARCHAR(100),
    discord_avatar_hash VARCHAR(128),
    first_login_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT operator_accounts_discord_user_id_unique UNIQUE (discord_user_id),
    CONSTRAINT operator_accounts_discord_user_id_length
        CHECK (char_length(discord_user_id) BETWEEN 1 AND 32),
    CONSTRAINT operator_accounts_username_length
        CHECK (char_length(btrim(discord_username)) BETWEEN 1 AND 100),
    CONSTRAINT operator_accounts_global_display_name_length
        CHECK (
            discord_global_display_name IS NULL
            OR char_length(discord_global_display_name) BETWEEN 1 AND 100
        ),
    CONSTRAINT operator_accounts_avatar_hash_length
        CHECK (
            discord_avatar_hash IS NULL
            OR char_length(discord_avatar_hash) BETWEEN 1 AND 128
        )
);
