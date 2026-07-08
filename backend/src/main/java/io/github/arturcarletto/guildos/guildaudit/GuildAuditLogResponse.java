package io.github.arturcarletto.guildos.guildaudit;

import java.util.List;

record GuildAuditLogResponse(String guildId, List<GuildAuditLogEntryResponse> events) {
}
