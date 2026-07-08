package io.github.arturcarletto.guildos.guildaudit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
class GuildAuditStore {

    private final GuildAuditEventRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    GuildAuditStore(GuildAuditEventRepository repository, EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    void append(
            UUID registeredGuildId,
            UUID operatorId,
            GuildAuditActorType actorType,
            GuildAuditEventType eventType) {
        Objects.requireNonNull(registeredGuildId, "registeredGuildId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (actorType == GuildAuditActorType.OPERATOR && operatorId == null) {
            throw new IllegalArgumentException("operatorId is required for operator audit events");
        }
        Instant now = clock.instant();
        repository.save(GuildAuditEvent.create(registeredGuildId, operatorId, actorType, eventType, now));
    }

    @Transactional(readOnly = true)
    List<GuildAuditEvent> find(
            UUID registeredGuildId,
            GuildAuditEventType eventType,
            Instant from,
            Instant to,
            int limit) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<GuildAuditEvent> query = criteriaBuilder.createQuery(GuildAuditEvent.class);
        Root<GuildAuditEvent> event = query.from(GuildAuditEvent.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(event.get("registeredGuildId"), registeredGuildId));
        if (eventType != null) {
            predicates.add(criteriaBuilder.equal(event.get("eventType"), eventType));
        }
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(event.get("occurredAt"), from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThan(event.get("occurredAt"), to));
        }
        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(
                criteriaBuilder.desc(event.get("occurredAt")),
                criteriaBuilder.desc(event.get("createdAt")),
                criteriaBuilder.desc(event.get("id")));
        return entityManager.createQuery(query)
                .setMaxResults(limit)
                .getResultList();
    }
}
