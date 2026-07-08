package io.github.arturcarletto.guildos.guildaudit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface GuildAuditEventRepository extends JpaRepository<GuildAuditEvent, UUID> {
}
