package inside.audit;

import discord4j.core.object.entity.*;
import discord4j.core.spec.*;
import discord4j.rest.util.Permission;
import inside.data.entity.*;
import inside.data.entity.base.NamedReference;
import inside.service.*;
import inside.util.DiscordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.context.ContextView;
import reactor.util.function.Tuple2;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.*;
import java.util.List;

import static inside.util.ContextUtil.*;

public abstract class BaseAuditProvider implements AuditProvider{
    public static final String MESSAGE_TXT = "message.txt";

    @Autowired
    protected MessageService messageService;

    @Autowired
    protected DiscordService discordService;

    @Override
    public Mono<Void> send(AuditConfig config, AuditAction action, List<Tuple2<String, InputStream>> attachments){
        return Mono.deferContextual(ctx -> Mono.justOrEmpty(config.logChannelId())
                .flatMap(discordService::getTextChannelById)
                .filter(ignored -> config.isEnabled(action.type()))
                .filterWhen(channel -> channel.getEffectivePermissions(discordService.gateway().getSelfId())
                        .map(set -> set.contains(Permission.SEND_MESSAGES))
                        .filterWhen(bool -> bool ? Mono.just(true) : discordService.gateway()
                                .getGuildById(action.guildId())
                                .flatMap(Guild::getOwner)
                                .flatMap(User::getPrivateChannel)
                                .flatMap(dm -> dm.createMessage(messageService.format(ctx, "audit.permission-denied",
                                        DiscordUtil.getChannelMention(config.logChannelId()
                                                .orElseThrow(IllegalStateException::new))))) // asserted above
                                .thenReturn(false)))
                .flatMap(channel -> channel.createMessage(spec -> {
                    spec.setEmbed(embed -> build(action, ctx, spec, embed.setColor(action.type().color)));
                    attachments.forEach(TupleUtils.consumer(spec::addFile));
                }))
                .then());
    }

    protected void addTimestamp(ContextView context, AuditAction action, EmbedCreateSpec embed){
        ZoneId zoneId = context.get(KEY_TIMEZONE);
        embed.setFooter(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
                .withLocale(context.get(KEY_LOCALE))
                .withZone(zoneId)
                .format(action.timestamp()), null);
    }

    protected String getChannelReference(ContextView context, NamedReference reference){
        return getReferenceContent(context, reference, true);
    }

    protected String getUserReference(ContextView context, NamedReference reference){
        return getReferenceContent(context, reference, false);
    }

    protected String getShortReference(ContextView context, NamedReference reference){
        return messageService.format(context, "audit.reference.short", reference.name());
    }

    private String getReferenceContent(ContextView context, NamedReference reference, boolean channel){
        return messageService.format(context, "audit.reference", reference.name(),
                (channel ? "<#" : "<@") + reference.id() + ">");
    }

    protected abstract void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed);
}
