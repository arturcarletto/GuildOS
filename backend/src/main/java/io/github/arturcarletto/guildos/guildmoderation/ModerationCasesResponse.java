package io.github.arturcarletto.guildos.guildmoderation;

import java.util.List;

record ModerationCasesResponse(String guildId, List<ModerationCaseResponse> cases) {
}
