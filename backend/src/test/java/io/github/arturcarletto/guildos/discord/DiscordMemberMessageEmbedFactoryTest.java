package io.github.arturcarletto.guildos.discord;

import java.time.Instant;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.Test;

import io.github.arturcarletto.guildos.discord.DiscordMemberMessageChannelResolver.ChannelDisplay;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageView;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageAppearance;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageState;
import io.github.arturcarletto.guildos.guildmembermessage.RenderedMemberMessage;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordMemberMessageEmbedFactoryTest {

    private final DiscordMemberMessageEmbedFactory factory = new DiscordMemberMessageEmbedFactory();

    @Test
    void welcomePublicMessageWhitelistsOnlyTheJoiningUserAndCarriesTheFullEmbed() {
        RenderedMemberMessage rendered = new RenderedMemberMessage(
                MemberMessageKind.WELCOME, "Welcome to Heaven!", "Hey Artur", 0x57F287,
                "https://example.com/banner.png", "Welcome • Heaven", 1248, true, "<@100>",
                "Read the rules", "https://example.com/rules");

        MessageCreateData message = factory.publicMessage(
                rendered, "https://cdn/avatar.png", Instant.parse("2026-07-03T00:00:00Z"), "100");

        assertThat(message.getContent()).isEqualTo("<@100>");
        assertThat(message.getMentionedUsers()).containsExactly("100");
        assertThat(message.getAllowedMentions()).doesNotContain(MentionType.EVERYONE, MentionType.HERE);
        assertThat(message.getComponents()).isNotEmpty();

        MessageEmbed embed = message.getEmbeds().get(0);
        assertThat(embed.getTitle()).isEqualTo("Welcome to Heaven!");
        assertThat(embed.getDescription()).isEqualTo("Hey Artur");
        assertThat(embed.getColorRaw()).isEqualTo(0x57F287);
        assertThat(embed.getThumbnail().getUrl()).isEqualTo("https://cdn/avatar.png");
        assertThat(embed.getImage().getUrl()).isEqualTo("https://example.com/banner.png");
        assertThat(embed.getFooter().getText()).isEqualTo("Welcome • Heaven");
        assertThat(embed.getTimestamp()).isNotNull();
        assertThat(embed.getFields()).anySatisfy(field ->
                assertThat(field.getName()).isEqualTo("Member count"));
    }

    @Test
    void goodbyePublicMessageMentionsNobodyAndHasNoButton() {
        RenderedMemberMessage rendered = new RenderedMemberMessage(
                MemberMessageKind.GOODBYE, "A member has left", "Goodbye Artur", 0xED4245,
                null, "Goodbye • Heaven", 1247, false, "", null, null);

        MessageCreateData message = factory.publicMessage(
                rendered, "https://cdn/avatar.png", Instant.now(), null);

        assertThat(message.getContent()).isEmpty();
        assertThat(message.getMentionedUsers()).isEmpty();
        assertThat(message.getComponents()).isEmpty();
        assertThat(message.getEmbeds().get(0).getImage()).isNull();
    }

    @Test
    void previewMessageNeverWhitelistsAnyUser() {
        RenderedMemberMessage rendered = new RenderedMemberMessage(
                MemberMessageKind.WELCOME, "Welcome", "Hey Artur", 0x57F287,
                null, "Footer", 5, true, "<@100>", null, null);

        MessageCreateData preview = factory.previewMessage(rendered, "https://cdn/avatar.png", Instant.now());

        assertThat(preview.getMentionedUsers()).isEmpty();
    }

    @Test
    void statusEmbedShowsConfigurationWithoutAnyVersion() {
        MemberMessageAppearance appearance = new MemberMessageAppearance(
                "Welcome to {server}!", "Hi {member}", 0x57F287, "https://example.com/i.png",
                "Welcome • {server}", true, false, "Rules", "https://example.com/rules");
        GuildMemberMessageView view = new GuildMemberMessageView(
                MemberMessageState.CONFIGURED, "Heaven", MemberMessageKind.WELCOME, true,
                "820000000000000001", appearance, null);

        MessageEmbed embed = factory.status(view, new ChannelDisplay("#welcome", ""));

        assertThat(embed.getTitle()).isEqualTo("Welcome configuration");
        assertThat(embed.getFields()).anySatisfy(field ->
                assertThat(field.getName()).isEqualTo("Channel"));
        assertThat(embed.getFields()).anySatisfy(field ->
                assertThat(field.getName()).isEqualTo("Accent color"));
        assertThat(embed.toData().toString())
                .doesNotContain("version", "Version", "820000000000000001");
    }
}
