package inside.event;

import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.channel.GuildChannel;
import inside.audit.*;
import inside.data.service.EntityRetriever;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import static inside.audit.AuditActionType.*;
import static inside.util.ContextUtil.*;
import static reactor.function.TupleUtils.function;

@Component
public class VoiceEventHandler extends ReactiveEventAdapter{
    @Autowired
    private AuditService auditService;

    @Autowired
    private EntityRetriever entityRetriever;

    @Override
    public Publisher<?> onVoiceStateUpdate(VoiceStateUpdateEvent event){
        Snowflake guildId = event.getCurrent().getGuildId();

        Mono<Context> initContext = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .map(guildConfig -> Context.of(KEY_LOCALE, guildConfig.locale(),
                        KEY_TIMEZONE, guildConfig.timeZone()));

        if(event.isMoveEvent()){
            Mono<GuildChannel> old = Mono.justOrEmpty(event.getOld())
                    .flatMap(VoiceState::getChannel)
                    .cast(GuildChannel.class);

            return initContext.flatMap(context -> Mono.zip(old, event.getCurrent().getUser(), event.getCurrent().getChannel())
                    .flatMap(function((oldChannel, user, currentChannel) -> auditService.newBuilder(guildId, VOICE_MOVE)
                            .withAttribute(Attribute.OLD_CHANNEL, AuditActionBuilder.getReference(oldChannel))
                            .withChannel(currentChannel)
                            .withUser(user)
                            .save()))
                    .contextWrite(context));
        }

        return initContext.flatMap(context -> Mono.justOrEmpty(event.getOld())
                .defaultIfEmpty(event.getCurrent())
                .flatMap(state -> Mono.zip(state.getChannel(), state.getUser()))
                .flatMap(function((channel, user) -> auditService.newBuilder(guildId, event.isLeaveEvent() ? VOICE_LEAVE : VOICE_JOIN)
                        .withChannel(channel)
                        .withUser(user)
                        .save()))
                .contextWrite(context));
    }
}
