package inside.event;

import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.channel.GuildChannel;
import inside.audit.*;
import inside.data.service.EntityRetriever;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import static inside.util.ContextUtil.*;
import static reactor.function.TupleUtils.function;

@Component
public class ReactionEventHandler extends ReactiveEventAdapter{

    @Autowired
    private EntityRetriever entityRetriever;

    @Autowired
    private AuditService auditService;

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        Mono<Context> initContext = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .map(guildConfig -> Context.of(KEY_LOCALE, guildConfig.locale(),
                        KEY_TIMEZONE, guildConfig.timeZone()));

        return initContext.flatMap(context -> Mono.zip(event.getUser().flatMap(user -> user.asMember(guildId)),
                event.getChannel().ofType(GuildChannel.class))
                .flatMap(function((member, channel) -> auditService.newBuilder(guildId, AuditActionType.REACTION_ADD)
                        .withUser(member)
                        .withChannel(channel)
                        .withAttribute(Attribute.MESSAGE_ID, event.getMessageId())
                        .withAttribute(Attribute.REACTION_EMOJI, event.getEmoji())
                        .save()))
                .contextWrite(context));
    }

    @Override
    public Publisher<?> onReactionRemove(ReactionRemoveEvent event){
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        Mono<Context> initContext = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .map(guildConfig -> Context.of(KEY_LOCALE, guildConfig.locale(),
                        KEY_TIMEZONE, guildConfig.timeZone()));

        return initContext.flatMap(context -> Mono.zip(event.getUser().flatMap(user -> user.asMember(guildId)),
                event.getChannel().ofType(GuildChannel.class))
                .flatMap(function((member, channel) -> auditService.newBuilder(guildId, AuditActionType.REACTION_REMOVE)
                        .withUser(member)
                        .withChannel(channel)
                        .withAttribute(Attribute.MESSAGE_ID, event.getMessageId())
                        .withAttribute(Attribute.REACTION_EMOJI, event.getEmoji())
                        .save()))
                .contextWrite(context));
    }

    @Override
    public Publisher<?> onReactionRemoveAll(ReactionRemoveAllEvent event){
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        Mono<Context> initContext = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .map(guildConfig -> Context.of(KEY_LOCALE, guildConfig.locale(),
                        KEY_TIMEZONE, guildConfig.timeZone()));

        return initContext.flatMap(context -> event.getChannel().ofType(GuildChannel.class)
                .flatMap(channel -> auditService.newBuilder(guildId, AuditActionType.REACTION_REMOVE_ALL)
                        .withChannel(channel)
                        .withAttribute(Attribute.MESSAGE_ID, event.getMessageId())
                        .save())
                .contextWrite(context));
    }
}
