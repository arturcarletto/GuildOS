CREATE TABLE guild_os.guild_activity_events (
    id UUID PRIMARY KEY,
    source_event_id VARCHAR(200) NOT NULL,
    guild_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    subject_discord_id VARCHAR(32) NOT NULL,
    channel_discord_id VARCHAR(32),
    actor_discord_user_id VARCHAR(32),
    actor_is_bot BOOLEAN,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    schema_version SMALLINT NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_failure_category VARCHAR(120),
    CONSTRAINT guild_activity_events_source_event_unique UNIQUE (source_event_id),
    CONSTRAINT guild_activity_events_guild_fk
        FOREIGN KEY (guild_id) REFERENCES guild_os.guilds (id),
    CONSTRAINT guild_activity_events_source_event_length
        CHECK (char_length(btrim(source_event_id)) BETWEEN 1 AND 200),
    CONSTRAINT guild_activity_events_event_type_check
        CHECK (event_type IN (
            'MEMBER_JOINED',
            'MEMBER_LEFT',
            'MESSAGE_CREATED',
            'MESSAGE_EDITED',
            'MESSAGE_DELETED'
        )),
    CONSTRAINT guild_activity_events_status_check
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'DEAD')),
    CONSTRAINT guild_activity_events_schema_version_check
        CHECK (schema_version = 1),
    CONSTRAINT guild_activity_events_attempt_count_check
        CHECK (attempt_count >= 0),
    CONSTRAINT guild_activity_events_subject_snowflake_check
        CHECK (subject_discord_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT guild_activity_events_channel_snowflake_check
        CHECK (channel_discord_id IS NULL OR channel_discord_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT guild_activity_events_actor_snowflake_check
        CHECK (actor_discord_user_id IS NULL OR actor_discord_user_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT guild_activity_events_message_channel_check
        CHECK (
            (event_type IN ('MESSAGE_CREATED', 'MESSAGE_EDITED', 'MESSAGE_DELETED')
                AND channel_discord_id IS NOT NULL)
            OR (event_type IN ('MEMBER_JOINED', 'MEMBER_LEFT')
                AND channel_discord_id IS NULL)
        ),
    CONSTRAINT guild_activity_events_message_created_actor_check
        CHECK (
            event_type <> 'MESSAGE_CREATED'
            OR (
                (actor_discord_user_id IS NULL AND actor_is_bot IS NULL)
                OR (actor_discord_user_id IS NOT NULL AND actor_is_bot IS NOT NULL)
            )
        ),
    CONSTRAINT guild_activity_events_member_actor_check
        CHECK (
            event_type NOT IN ('MEMBER_JOINED', 'MEMBER_LEFT')
            OR (actor_discord_user_id IS NOT NULL AND actor_is_bot IS NOT NULL)
        ),
    CONSTRAINT guild_activity_events_update_delete_actor_check
        CHECK (
            event_type NOT IN ('MESSAGE_EDITED', 'MESSAGE_DELETED')
            OR (actor_discord_user_id IS NULL AND actor_is_bot IS NULL)
        ),
    CONSTRAINT guild_activity_events_locked_status_check
        CHECK (
            (processing_status = 'PROCESSING' AND locked_at IS NOT NULL AND processed_at IS NULL)
            OR (processing_status <> 'PROCESSING' AND locked_at IS NULL)
        ),
    CONSTRAINT guild_activity_events_processed_status_check
        CHECK (
            (processing_status = 'PROCESSED' AND processed_at IS NOT NULL)
            OR (processing_status <> 'PROCESSED' AND processed_at IS NULL)
        ),
    CONSTRAINT guild_activity_events_failure_category_check
        CHECK (
            last_failure_category IS NULL
            OR char_length(btrim(last_failure_category)) BETWEEN 1 AND 120
        )
);

CREATE INDEX guild_activity_events_claim_idx
    ON guild_os.guild_activity_events (processing_status, available_at, received_at);

CREATE INDEX guild_activity_events_stale_processing_idx
    ON guild_os.guild_activity_events (processing_status, locked_at)
    WHERE processing_status = 'PROCESSING';

CREATE INDEX guild_activity_events_guild_occurred_idx
    ON guild_os.guild_activity_events (guild_id, occurred_at);

CREATE TABLE guild_os.guild_activity_hourly (
    guild_id UUID NOT NULL,
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    message_created_count BIGINT NOT NULL DEFAULT 0,
    distinct_message_edited_count BIGINT NOT NULL DEFAULT 0,
    message_deleted_count BIGINT NOT NULL DEFAULT 0,
    member_joined_count BIGINT NOT NULL DEFAULT 0,
    member_left_count BIGINT NOT NULL DEFAULT 0,
    human_message_count BIGINT NOT NULL DEFAULT 0,
    bot_message_count BIGINT NOT NULL DEFAULT 0,
    active_member_count BIGINT NOT NULL DEFAULT 0,
    active_channel_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT guild_activity_hourly_pk PRIMARY KEY (guild_id, bucket_start),
    CONSTRAINT guild_activity_hourly_guild_fk
        FOREIGN KEY (guild_id) REFERENCES guild_os.guilds (id),
    CONSTRAINT guild_activity_hourly_counts_check
        CHECK (
            message_created_count >= 0
            AND distinct_message_edited_count >= 0
            AND message_deleted_count >= 0
            AND member_joined_count >= 0
            AND member_left_count >= 0
            AND human_message_count >= 0
            AND bot_message_count >= 0
            AND active_member_count >= 0
            AND active_channel_count >= 0
        )
);

CREATE INDEX guild_activity_hourly_guild_bucket_idx
    ON guild_os.guild_activity_hourly (guild_id, bucket_start);

CREATE TABLE guild_os.guild_activity_hourly_members (
    guild_id UUID NOT NULL,
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    discord_user_id VARCHAR(32) NOT NULL,
    CONSTRAINT guild_activity_hourly_members_pk
        PRIMARY KEY (guild_id, bucket_start, discord_user_id),
    CONSTRAINT guild_activity_hourly_members_hour_fk
        FOREIGN KEY (guild_id, bucket_start)
        REFERENCES guild_os.guild_activity_hourly (guild_id, bucket_start)
        ON DELETE CASCADE,
    CONSTRAINT guild_activity_hourly_members_user_snowflake_check
        CHECK (discord_user_id ~ '^[0-9]{1,20}$')
);

CREATE TABLE guild_os.guild_activity_hourly_channels (
    guild_id UUID NOT NULL,
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    discord_channel_id VARCHAR(32) NOT NULL,
    CONSTRAINT guild_activity_hourly_channels_pk
        PRIMARY KEY (guild_id, bucket_start, discord_channel_id),
    CONSTRAINT guild_activity_hourly_channels_hour_fk
        FOREIGN KEY (guild_id, bucket_start)
        REFERENCES guild_os.guild_activity_hourly (guild_id, bucket_start)
        ON DELETE CASCADE,
    CONSTRAINT guild_activity_hourly_channels_channel_snowflake_check
        CHECK (discord_channel_id ~ '^[0-9]{1,20}$')
);
