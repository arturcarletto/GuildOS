CREATE TABLE guild_os.guild_audit_events (
    id UUID PRIMARY KEY,
    registered_guild_id UUID NOT NULL,
    operator_id UUID,
    event_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    summary VARCHAR(240) NOT NULL,
    target_type VARCHAR(64),
    target_label VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT guild_audit_events_registered_guild_fk
        FOREIGN KEY (registered_guild_id) REFERENCES guild_os.guilds (id) ON DELETE CASCADE,
    CONSTRAINT guild_audit_events_operator_fk
        FOREIGN KEY (operator_id) REFERENCES guild_os.operator_accounts (id) ON DELETE SET NULL,
    CONSTRAINT guild_audit_events_type_valid CHECK (event_type IN (
        'GUILD_ONBOARDING_CREATED',
        'GUILD_ONBOARDING_REACTIVATED',
        'GUILD_ACCESS_ROLE_UPDATED',
        'GUILD_ACCESS_REVOKED',
        'GUILD_SETTINGS_UPDATED',
        'WELCOME_CONFIGURED',
        'WELCOME_TOGGLED',
        'GOODBYE_CONFIGURED',
        'GOODBYE_TOGGLED',
        'CHANNEL_METADATA_SYNCED'
    )),
    CONSTRAINT guild_audit_events_actor_type_valid
        CHECK (actor_type IN ('OPERATOR', 'SYSTEM', 'DISCORD')),
    CONSTRAINT guild_audit_events_summary_nonblank
        CHECK (char_length(btrim(summary)) BETWEEN 1 AND 240),
    CONSTRAINT guild_audit_events_target_type_valid CHECK (
        target_type IS NULL OR target_type IN (
            'GUILD_SETTINGS',
            'WELCOME_MESSAGE',
            'GOODBYE_MESSAGE',
            'CHANNEL_SYNC',
            'ONBOARDING'
        )
    ),
    CONSTRAINT guild_audit_events_target_label_bounded CHECK (
        target_label IS NULL OR char_length(btrim(target_label)) BETWEEN 1 AND 120
    )
);

CREATE INDEX guild_audit_events_guild_occurred_idx
    ON guild_os.guild_audit_events (registered_guild_id, occurred_at DESC, id DESC);

CREATE INDEX guild_audit_events_guild_type_occurred_idx
    ON guild_os.guild_audit_events (registered_guild_id, event_type, occurred_at DESC, id DESC);
