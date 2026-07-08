package io.github.arturcarletto.guildos.guildactivity;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class GuildActivityScheduler {

    private final GuildActivityProcessor processor;
    private final GuildActivityProcessorProperties properties;

    GuildActivityScheduler(GuildActivityProcessor processor, GuildActivityProcessorProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${guildos.activity.processing.fixed-delay-ms:10000}")
    void processAvailableEvents() {
        if (properties.isEnabled()) {
            processor.processAvailableBatch();
        }
    }
}
