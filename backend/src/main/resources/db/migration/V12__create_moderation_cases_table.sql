CREATE TABLE guild_os.moderation_cases (
    id UUID PRIMARY KEY,
    public_case_id VARCHAR(40) NOT NULL UNIQUE,
    registered_guild_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_discord_user_id VARCHAR(20) NOT NULL,
    duration_minutes INTEGER,
    status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    summary VARCHAR(240) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT moderation_cases_registered_guild_fk
        FOREIGN KEY (registered_guild_id) REFERENCES guild_os.guilds (id) ON DELETE CASCADE,
    CONSTRAINT moderation_cases_public_case_id_nonblank
        CHECK (char_length(btrim(public_case_id)) BETWEEN 1 AND 40),
    CONSTRAINT moderation_cases_action_type_valid
        CHECK (action_type IN ('MEMBER_TIMEOUT_CREATED')),
    CONSTRAINT moderation_cases_target_type_valid
        CHECK (target_type IN ('DISCORD_USER')),
    CONSTRAINT moderation_cases_target_discord_user_id_valid
        CHECK (target_discord_user_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT moderation_cases_duration_minutes_valid
        CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 40320),
    CONSTRAINT moderation_cases_status_valid
        CHECK (status IN ('COMPLETED')),
    CONSTRAINT moderation_cases_summary_nonblank
        CHECK (char_length(btrim(summary)) BETWEEN 1 AND 240)
);

CREATE INDEX moderation_cases_guild_occurred_idx
    ON guild_os.moderation_cases (registered_guild_id, occurred_at DESC, public_case_id DESC);

CREATE INDEX moderation_cases_guild_action_occurred_idx
    ON guild_os.moderation_cases (registered_guild_id, action_type, occurred_at DESC, public_case_id DESC);
