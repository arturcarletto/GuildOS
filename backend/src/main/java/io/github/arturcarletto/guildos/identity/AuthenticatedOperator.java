package io.github.arturcarletto.guildos.identity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class AuthenticatedOperator implements OAuth2User {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(
            new SimpleGrantedAuthority("ROLE_OPERATOR"));

    private final OperatorIdentity identity;
    private final Map<String, Object> attributes;

    public AuthenticatedOperator(OperatorIdentity identity) {
        this.identity = identity;
        Map<String, Object> safeAttributes = new LinkedHashMap<>();
        safeAttributes.put("operator_id", identity.operatorId());
        safeAttributes.put("discord_user_id", identity.discordUserId());
        safeAttributes.put("username", identity.username());
        safeAttributes.put("display_name", identity.displayName());
        safeAttributes.put("avatar_hash", identity.avatarHash());
        attributes = Collections.unmodifiableMap(safeAttributes);
    }

    public UUID operatorId() {
        return identity.operatorId();
    }

    public String discordUserId() {
        return identity.discordUserId();
    }

    public String username() {
        return identity.username();
    }

    public String displayName() {
        return identity.displayName();
    }

    public String avatarHash() {
        return identity.avatarHash();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getName() {
        return identity.operatorId().toString();
    }

    @Override
    public String toString() {
        return "AuthenticatedOperator{operatorId=%s, discordUserId='%s', username='%s'}"
                .formatted(identity.operatorId(), identity.discordUserId(), identity.username());
    }
}
