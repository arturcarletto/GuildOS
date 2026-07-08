package io.github.arturcarletto.guildos.platform;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlatformValueObjectsTest {

    @Test
    void identifiersRejectABlankExternalId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformCommunityId.of(CommunityPlatform.DISCORD, "  "))
                .withMessageContaining("externalId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformChannelId.of(CommunityPlatform.TELEGRAM, ""))
                .withMessageContaining("externalId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformActorId.of(CommunityPlatform.TELEGRAM, null))
                .withMessageContaining("externalId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformMessageId.of(CommunityPlatform.DISCORD, null))
                .withMessageContaining("externalId");
    }

    @Test
    void identifiersRejectANullPlatform() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformCommunityId.of(null, "123"))
                .withMessageContaining("platform");
    }

    @Test
    void identifiersTrimTheExternalIdForConsistentEquality() {
        PlatformChannelId trimmed = PlatformChannelId.of(CommunityPlatform.TELEGRAM, "  456 ");
        PlatformChannelId plain = PlatformChannelId.of(CommunityPlatform.TELEGRAM, "456");

        assertThat(trimmed.externalId()).isEqualTo("456");
        assertThat(trimmed).isEqualTo(plain);
        assertThat(trimmed).hasSameHashCodeAs(plain);
    }

    @Test
    void identifiersOnDifferentPlatformsAreNotEqual() {
        PlatformCommunityId discord = PlatformCommunityId.of(CommunityPlatform.DISCORD, "100");
        PlatformCommunityId telegram = PlatformCommunityId.of(CommunityPlatform.TELEGRAM, "100");

        assertThat(discord).isNotEqualTo(telegram);
    }

    @Test
    void botCommandMatchesTheCommonInvocationForms() {
        PlatformBotCommand ping = PlatformBotCommand.of("/PING", "Check that GuildOS is online");

        assertThat(ping.name()).isEqualTo("ping");
        assertThat(ping.matches("/ping")).isTrue();
        assertThat(ping.matches("  /ping  ")).isTrue();
        assertThat(ping.matches("/ping@GuildOsBot")).isTrue();
        assertThat(ping.matches("/ping now please")).isTrue();
        assertThat(ping.matches("/status")).isFalse();
        assertThat(ping.matches("ping")).isFalse();
        assertThat(ping.matches("hello /ping")).isFalse();
        assertThat(ping.matches(null)).isFalse();
    }

    @Test
    void botCommandRejectsABlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformBotCommand.of("  ", "desc"))
                .withMessageContaining("command name");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PlatformBotCommand.of("/", "desc"))
                .withMessageContaining("command name");
    }

    @Test
    void incomingEventRequiresPlatformAndTimestamp() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IncomingCommunityEvent(
                        CommunityPlatform.TELEGRAM,
                        null,
                        PlatformChannelId.of(CommunityPlatform.DISCORD, "1"),
                        null,
                        null,
                        Instant.EPOCH))
                .withMessageContaining("channel id platform");

        assertThat(new IncomingCommunityEvent(
                CommunityPlatform.TELEGRAM,
                null,
                PlatformChannelId.of(CommunityPlatform.TELEGRAM, "1"),
                PlatformActorId.of(CommunityPlatform.TELEGRAM, "2"),
                PlatformMessageId.of(CommunityPlatform.TELEGRAM, "3"),
                Instant.EPOCH))
                .satisfies(event -> {
                    assertThat(event.platform()).isEqualTo(CommunityPlatform.TELEGRAM);
                    assertThat(event.channel().externalId()).isEqualTo("1");
                });
    }
}
