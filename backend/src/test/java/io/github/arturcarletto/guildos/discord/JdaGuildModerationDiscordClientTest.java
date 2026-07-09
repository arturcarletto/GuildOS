package io.github.arturcarletto.guildos.discord;

import java.util.List;
import java.util.Optional;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.junit.jupiter.api.Test;

import io.github.arturcarletto.guildos.guildmoderation.MemberSearchQuery;
import io.github.arturcarletto.guildos.guildmoderation.MemberSearchResult;
import io.github.arturcarletto.guildos.guildmoderation.MemberSearchResultMember;
import io.github.arturcarletto.guildos.guildmoderation.ModerationDiscordActionException;
import io.github.arturcarletto.guildos.guildmoderation.ModerationFailureCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdaGuildModerationDiscordClientTest {

    private static final String GUILD_ID = "200000000000000916";
    private static final String USER_ID = "300000000000000916";

    private final DiscordGateway gateway = mock(DiscordGateway.class);
    private final JDA jda = mock(JDA.class);
    private final Guild guild = mock(Guild.class);
    private final JdaGuildModerationDiscordClient client = new JdaGuildModerationDiscordClient(gateway);

    @Test
    void exactIdLookupReturnsSingleMappedMember() {
        connectedGuild();
        Member found = member(USER_ID, "some_user", "Some User", false);
        CacheRestAction<Member> action = memberLookup();
        when(guild.retrieveMemberById(USER_ID)).thenReturn(action);
        when(action.complete()).thenReturn(found);

        MemberSearchResult result = client.searchMembers(new MemberSearchQuery(GUILD_ID, USER_ID, true, 10));

        assertThat(result.members()).containsExactly(
                new MemberSearchResultMember(USER_ID, "some_user", "Some User", false));
        // JDA types must not leak: results are the moderation-owned DTO, never a JDA Member.
        assertThat(result.members().get(0)).isInstanceOf(MemberSearchResultMember.class);
    }

    @Test
    void exactIdLookupTreatsUnknownMemberAsEmptyResult() {
        connectedGuild();
        CacheRestAction<Member> action = memberLookup();
        when(guild.retrieveMemberById(USER_ID)).thenReturn(action);
        ErrorResponseException notFound = mock(ErrorResponseException.class);
        when(notFound.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MEMBER);
        when(action.complete()).thenThrow(notFound);

        MemberSearchResult result = client.searchMembers(new MemberSearchQuery(GUILD_ID, USER_ID, true, 10));

        assertThat(result.members()).isEmpty();
    }

    @Test
    void textSearchReturnsResultsCappedToRequestedLimit() {
        connectedGuild();
        List<Member> matches = List.of(
                member("1", "art_one", "Art One", false),
                member("2", "art_two", "Art Two", false),
                member("3", "art_three", "Art Three", true),
                member("4", "art_four", "Art Four", false),
                member("5", "art_five", "Art Five", false));
        @SuppressWarnings("unchecked")
        Task<List<Member>> task = mock(Task.class);
        when(guild.retrieveMembersByPrefix(eq("art"), anyInt())).thenReturn(task);
        when(task.get()).thenReturn(matches);

        MemberSearchResult result = client.searchMembers(new MemberSearchQuery(GUILD_ID, "art", false, 3));

        assertThat(result.members()).hasSize(3);
        assertThat(result.members().get(0).username()).isEqualTo("art_one");
        assertThat(result.members().get(2).bot()).isTrue();
    }

    @Test
    void discordFailureIsMappedToSafeApplicationError() {
        connectedGuild();
        @SuppressWarnings("unchecked")
        Task<List<Member>> task = mock(Task.class);
        when(guild.retrieveMembersByPrefix(eq("art"), anyInt())).thenReturn(task);
        when(task.get()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> client.searchMembers(new MemberSearchQuery(GUILD_ID, "art", false, 10)))
                .isInstanceOf(ModerationDiscordActionException.class)
                .extracting(exception -> ((ModerationDiscordActionException) exception).category())
                .isEqualTo(ModerationFailureCategory.DISCORD_UNAVAILABLE);
    }

    @Test
    void unresolvableGuildIsMappedToGuildUnavailable() {
        when(gateway.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById(GUILD_ID)).thenReturn(null);

        assertThatThrownBy(() -> client.searchMembers(new MemberSearchQuery(GUILD_ID, "art", false, 10)))
                .isInstanceOf(ModerationDiscordActionException.class)
                .extracting(exception -> ((ModerationDiscordActionException) exception).category())
                .isEqualTo(ModerationFailureCategory.GUILD_UNAVAILABLE);
    }

    @Test
    void disconnectedGatewayIsMappedToDiscordUnavailable() {
        when(gateway.jda()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.searchMembers(new MemberSearchQuery(GUILD_ID, "art", false, 10)))
                .isInstanceOf(ModerationDiscordActionException.class)
                .extracting(exception -> ((ModerationDiscordActionException) exception).category())
                .isEqualTo(ModerationFailureCategory.DISCORD_UNAVAILABLE);
    }

    private void connectedGuild() {
        when(gateway.jda()).thenReturn(Optional.of(jda));
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
    }

    @SuppressWarnings("unchecked")
    private static CacheRestAction<Member> memberLookup() {
        return mock(CacheRestAction.class);
    }

    private static Member member(String id, String username, String effectiveName, boolean bot) {
        Member member = mock(Member.class);
        User user = mock(User.class);
        when(member.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn(username);
        when(user.isBot()).thenReturn(bot);
        when(member.getEffectiveName()).thenReturn(effectiveName);
        return member;
    }
}
