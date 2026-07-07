-- Evolve the single-purpose welcome table into a shared member-message table that stores one
-- configuration per guild per lifecycle kind (WELCOME or GOODBYE), with rich embed appearance.

CREATE TABLE guild_os.guild_member_message_configurations (
    id UUID PRIMARY KEY,
    registered_guild_id UUID NOT NULL,
    message_kind VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    channel_id VARCHAR(20) NOT NULL,
    title_template VARCHAR(256) NOT NULL,
    description_template VARCHAR(1000) NOT NULL,
    accent_color INTEGER NOT NULL,
    image_url VARCHAR(512),
    footer_template VARCHAR(256) NOT NULL,
    mention_member BOOLEAN NOT NULL,
    include_bots BOOLEAN NOT NULL,
    button_label VARCHAR(80),
    button_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT guild_member_message_configurations_registered_guild_fk
        FOREIGN KEY (registered_guild_id) REFERENCES guild_os.guilds (id),
    CONSTRAINT guild_member_message_configurations_guild_kind_unique
        UNIQUE (registered_guild_id, message_kind),
    CONSTRAINT guild_member_message_configurations_kind_valid
        CHECK (message_kind IN ('WELCOME', 'GOODBYE')),
    CONSTRAINT guild_member_message_configurations_channel_id_valid
        CHECK (channel_id ~ '^[0-9]{1,20}$'),
    CONSTRAINT guild_member_message_configurations_title_nonblank
        CHECK (char_length(btrim(title_template)) BETWEEN 1 AND 256),
    CONSTRAINT guild_member_message_configurations_description_nonblank
        CHECK (char_length(btrim(description_template)) BETWEEN 1 AND 1000),
    CONSTRAINT guild_member_message_configurations_footer_nonblank
        CHECK (char_length(btrim(footer_template)) BETWEEN 1 AND 256),
    CONSTRAINT guild_member_message_configurations_accent_color_valid
        CHECK (accent_color BETWEEN 0 AND 16777215),
    CONSTRAINT guild_member_message_configurations_image_url_https
        CHECK (image_url IS NULL OR image_url LIKE 'https://%'),
    CONSTRAINT guild_member_message_configurations_button_pair
        CHECK (
            (button_label IS NULL AND button_url IS NULL)
            OR (button_label IS NOT NULL AND button_url IS NOT NULL)
        ),
    CONSTRAINT guild_member_message_configurations_button_label_length
        CHECK (button_label IS NULL OR char_length(btrim(button_label)) BETWEEN 1 AND 80),
    CONSTRAINT guild_member_message_configurations_button_url_https
        CHECK (button_url IS NULL OR button_url LIKE 'https://%'),
    CONSTRAINT guild_member_message_configurations_goodbye_no_mention
        CHECK (message_kind = 'WELCOME' OR mention_member = FALSE),
    CONSTRAINT guild_member_message_configurations_goodbye_no_button
        CHECK (message_kind = 'WELCOME' OR (button_label IS NULL AND button_url IS NULL)),
    CONSTRAINT guild_member_message_configurations_version_nonnegative
        CHECK (version >= 0)
);

-- Migrate every existing welcome configuration into a WELCOME member message, preserving the
-- channel, template (as the description), enabled state, timestamps and optimistic version.
-- 5763719 is the integer form of Discord green (#57F287).
INSERT INTO guild_os.guild_member_message_configurations (
    id,
    registered_guild_id,
    message_kind,
    enabled,
    channel_id,
    title_template,
    description_template,
    accent_color,
    image_url,
    footer_template,
    mention_member,
    include_bots,
    button_label,
    button_url,
    created_at,
    updated_at,
    version
)
SELECT
    id,
    registered_guild_id,
    'WELCOME',
    enabled,
    channel_id,
    'Welcome to {server}!',
    message_template,
    5763719,
    NULL,
    'Welcome • {server}',
    TRUE,
    FALSE,
    NULL,
    NULL,
    created_at,
    updated_at,
    version
FROM guild_os.guild_welcome_configurations;

DROP TABLE guild_os.guild_welcome_configurations;
