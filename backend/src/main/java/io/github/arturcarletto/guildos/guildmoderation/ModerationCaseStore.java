package io.github.arturcarletto.guildos.guildmoderation;

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
class ModerationCaseStore {

    private final ModerationCaseRepository repository;
    private final EntityManager entityManager;

    ModerationCaseStore(ModerationCaseRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    void append(ModerationCase moderationCase) {
        repository.save(Objects.requireNonNull(moderationCase, "moderationCase must not be null"));
    }

    @Transactional(readOnly = true)
    List<ModerationCase> find(
            UUID registeredGuildId,
            ModerationCaseActionType actionType,
            Instant from,
            Instant to,
            int limit) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ModerationCase> query = criteriaBuilder.createQuery(ModerationCase.class);
        Root<ModerationCase> moderationCase = query.from(ModerationCase.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(moderationCase.get("registeredGuildId"), registeredGuildId));
        if (actionType != null) {
            predicates.add(criteriaBuilder.equal(moderationCase.get("actionType"), actionType));
        }
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(moderationCase.get("occurredAt"), from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThan(moderationCase.get("occurredAt"), to));
        }
        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(
                criteriaBuilder.desc(moderationCase.get("occurredAt")),
                criteriaBuilder.desc(moderationCase.get("createdAt")),
                criteriaBuilder.desc(moderationCase.get("publicCaseId")));
        return entityManager.createQuery(query)
                .setMaxResults(limit)
                .getResultList();
    }
}
