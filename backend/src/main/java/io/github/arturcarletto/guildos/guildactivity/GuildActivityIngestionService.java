package io.github.arturcarletto.guildos.guildactivity;

import org.springframework.stereotype.Service;

@Service
public class GuildActivityIngestionService {

    private final GuildActivityIngestionStore store;
    private final GuildActivityMetrics metrics;

    GuildActivityIngestionService(GuildActivityIngestionStore store, GuildActivityMetrics metrics) {
        this.store = store;
        this.metrics = metrics;
    }

    public GuildActivityIngestionResult ingest(IngestGuildActivityCommand command) {
        try {
            GuildActivityCommandValidator.validate(command);
            GuildActivityIngestionResult result = store.insert(command);
            metrics.recordIngestion(command.eventType(), result.metricTag());
            return result;
        } catch (InvalidGuildActivityCommandException exception) {
            metrics.recordIngestion(command == null ? null : command.eventType(), "invalid");
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordIngestion(command == null ? null : command.eventType(), "failed");
            throw exception;
        }
    }
}
