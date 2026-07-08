package io.github.arturcarletto.guildos.guildmembermessage;

record GuildMemberMessageMutationResult(
        StoredGuildMemberMessage stored,
        GuildMemberMessageMutationKind mutationKind) {

    static GuildMemberMessageMutationResult created(StoredGuildMemberMessage stored) {
        return new GuildMemberMessageMutationResult(stored, GuildMemberMessageMutationKind.CREATED);
    }

    static GuildMemberMessageMutationResult updated(StoredGuildMemberMessage stored) {
        return new GuildMemberMessageMutationResult(stored, GuildMemberMessageMutationKind.UPDATED);
    }

    static GuildMemberMessageMutationResult unchanged(StoredGuildMemberMessage stored) {
        return new GuildMemberMessageMutationResult(stored, GuildMemberMessageMutationKind.UNCHANGED);
    }

    static GuildMemberMessageMutationResult enabled(StoredGuildMemberMessage stored) {
        return new GuildMemberMessageMutationResult(stored, GuildMemberMessageMutationKind.ENABLED);
    }

    static GuildMemberMessageMutationResult disabled(StoredGuildMemberMessage stored) {
        return new GuildMemberMessageMutationResult(stored, GuildMemberMessageMutationKind.DISABLED);
    }

    boolean changed() {
        return mutationKind != GuildMemberMessageMutationKind.UNCHANGED;
    }
}
