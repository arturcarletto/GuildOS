package io.github.arturcarletto.guildos.identity;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;

final class DiscordOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String INVALID_USER_INFO = "invalid_user_info";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final OperatorLoginService operatorLoginService;

    DiscordOAuth2UserService(OperatorLoginService operatorLoginService) {
        this(new DefaultOAuth2UserService(), operatorLoginService);
    }

    DiscordOAuth2UserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
            OperatorLoginService operatorLoginService) {
        this.delegate = delegate;
        this.operatorLoginService = operatorLoginService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User discordUser = delegate.loadUser(userRequest);
        Map<String, Object> attributes = discordUser.getAttributes();
        OperatorLoginCommand command = new OperatorLoginCommand(
                requiredString(attributes, "id"),
                requiredString(attributes, "username"),
                optionalString(attributes, "global_name"),
                optionalString(attributes, "avatar"));

        return new AuthenticatedOperator(operatorLoginService.login(command));
    }

    private String requiredString(Map<String, Object> attributes, String name) {
        String value = optionalString(attributes, name);
        if (!StringUtils.hasText(value)) {
            throw invalidUserInfo("Discord user-info did not include required attribute: " + name);
        }
        return value;
    }

    private String optionalString(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return StringUtils.hasText(stringValue) ? stringValue : null;
        }
        throw invalidUserInfo("Discord user-info attribute had an invalid type: " + name);
    }

    private OAuth2AuthenticationException invalidUserInfo(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(INVALID_USER_INFO), message);
    }
}
