CREATE TABLE guild_os.operator_guild_access (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL,
    registered_guild_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT operator_guild_access_operator_fk
        FOREIGN KEY (operator_id) REFERENCES guild_os.operator_accounts (id),
    CONSTRAINT operator_guild_access_guild_fk
        FOREIGN KEY (registered_guild_id) REFERENCES guild_os.guilds (id),
    CONSTRAINT operator_guild_access_operator_guild_unique UNIQUE (operator_id, registered_guild_id),
    CONSTRAINT operator_guild_access_role_check CHECK (role IN ('OWNER', 'ADMIN'))
);

CREATE INDEX operator_guild_access_active_by_operator_idx
    ON guild_os.operator_guild_access (operator_id)
    WHERE revoked_at IS NULL;

CREATE INDEX operator_guild_access_registered_guild_idx
    ON guild_os.operator_guild_access (registered_guild_id);
