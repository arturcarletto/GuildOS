ALTER TABLE guild_os.guild_audit_events
    DROP CONSTRAINT guild_audit_events_type_valid;

ALTER TABLE guild_os.guild_audit_events
    ADD CONSTRAINT guild_audit_events_type_valid CHECK (event_type IN (
        'GUILD_ONBOARDING_CREATED',
        'GUILD_ONBOARDING_REACTIVATED',
        'GUILD_ACCESS_ROLE_UPDATED',
        'GUILD_ACCESS_REVOKED',
        'GUILD_SETTINGS_UPDATED',
        'WELCOME_CONFIGURED',
        'WELCOME_TOGGLED',
        'GOODBYE_CONFIGURED',
        'GOODBYE_TOGGLED',
        'CHANNEL_METADATA_SYNCED',
        'MEMBER_TIMEOUT_CREATED'
    ));

ALTER TABLE guild_os.guild_audit_events
    DROP CONSTRAINT guild_audit_events_target_type_valid;

ALTER TABLE guild_os.guild_audit_events
    ADD CONSTRAINT guild_audit_events_target_type_valid CHECK (
        target_type IS NULL OR target_type IN (
            'GUILD_SETTINGS',
            'WELCOME_MESSAGE',
            'GOODBYE_MESSAGE',
            'CHANNEL_SYNC',
            'ONBOARDING',
            'MODERATION_ACTION'
        )
    );
