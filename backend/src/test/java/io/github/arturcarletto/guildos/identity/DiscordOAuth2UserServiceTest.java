package io.github.arturcarletto.guildos.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscordOAuth2UserServiceTest {

    @Test
    void mapsSafeDiscordAttributesAndPersistsTheOperator() {
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mockUserService();
        OperatorLoginService loginService = mock(OperatorLoginService.class);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("id", "discord-operator-1");
        attributes.put("username", "operator");
        attributes.put("global_name", "Guild Operator");
        attributes.put("avatar", "avatar-hash");
        attributes.put("locale", "not-propagated");
        when(delegate.loadUser(request)).thenReturn(discordUser(attributes));
        OperatorIdentity identity = new OperatorIdentity(
                UUID.randomUUID(),
                "discord-operator-1",
                "operator",
                "Guild Operator",
                "avatar-hash");
        when(loginService.login(new OperatorLoginCommand(
                "discord-operator-1",
                "operator",
                "Guild Operator",
                "avatar-hash"))).thenReturn(identity);
        DiscordOAuth2UserService service = new DiscordOAuth2UserService(delegate, loginService);

        AuthenticatedOperator principal = (AuthenticatedOperator) service.loadUser(request);

        assertThat(principal.operatorId()).isEqualTo(identity.operatorId());
        assertThat(principal.getAttributes())
                .containsOnlyKeys("operator_id", "discord_user_id", "username", "display_name", "avatar_hash")
                .doesNotContainKey("locale");
        verify(loginService).login(new OperatorLoginCommand(
                "discord-operator-1",
                "operator",
                "Guild Operator",
                "avatar-hash"));
    }

    @Test
    void rejectsMissingRequiredDiscordAttributes() {
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mockUserService();
        OperatorLoginService loginService = mock(OperatorLoginService.class);
        when(delegate.loadUser(request)).thenReturn(discordUser(Map.of("username", "operator")));
        DiscordOAuth2UserService service = new DiscordOAuth2UserService(delegate, loginService);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("required attribute: id");
        verifyNoInteractions(loginService);
    }

    @SuppressWarnings("unchecked")
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> mockUserService() {
        return mock(OAuth2UserService.class);
    }

    private OAuth2User discordUser(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                attributes.containsKey("id") ? "id" : "username");
    }
}
