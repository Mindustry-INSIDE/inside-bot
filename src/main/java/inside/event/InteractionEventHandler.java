package inside.event;

import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.interaction.SlashCommandEvent;
import inside.Settings;
import inside.data.service.EntityRetriever;
import inside.interaction.InteractionCommandEnvironment;
import inside.service.*;
import inside.util.ContextUtil;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class InteractionEventHandler extends ReactiveEventAdapter{

    @Autowired
    private EntityRetriever entityRetriever;

    @Autowired
    private MessageService messageService;

    @Autowired
    private Settings settings;

    @Autowired
    private DiscordService discordService;

    @Override
    public Publisher<?> onSlashCommand(SlashCommandEvent event){
        Mono<Context> initContext = Mono.justOrEmpty(event.getInteraction().getGuildId())
                .flatMap(guildId -> entityRetriever.getGuildConfigById(guildId)
                        .switchIfEmpty(entityRetriever.createGuildConfig(guildId)))
                .map(guildConfig -> Context.of(ContextUtil.KEY_LOCALE, guildConfig.locale(),
                        ContextUtil.KEY_TIMEZONE, guildConfig.timeZone()))
                .defaultIfEmpty(Context.of(ContextUtil.KEY_LOCALE, messageService.getDefaultLocale(),
                        ContextUtil.KEY_TIMEZONE, settings.getDefaults().getTimeZone()));

        return initContext.flatMap(context -> discordService.handle(InteractionCommandEnvironment.builder()
                .event(event)
                .context(context)
                .build())
                .contextWrite(context));
    }
}
