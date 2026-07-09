package io.github.arturcarletto.guildos.guildmoderation;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ModerationCaseRepository extends JpaRepository<ModerationCase, UUID> {
}
