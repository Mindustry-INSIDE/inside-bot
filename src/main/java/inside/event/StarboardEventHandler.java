package inside.event;

import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.reaction.*;
import discord4j.rest.util.*;
import inside.data.entity.*;
import inside.data.service.EntityRetriever;
import inside.service.MessageService;
import inside.util.*;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.*;
import reactor.math.MathFlux;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static inside.util.ContextUtil.*;
import static reactor.bool.BooleanUtils.not;
import static reactor.function.TupleUtils.function;

@Component
public class StarboardEventHandler extends ReactiveEventAdapter{
    private static final Color offsetColor = Color.of(0xffefc0), targetColor = Color.of(0xdaa520);
    private static final float lerpStep = 1.0E-05f;

    @Autowired
    private EntityRetriever entityRetriever;

    @Autowired
    private MessageService messageService;

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        ReactionEmoji emoji = event.getEmoji();
        Snowflake guildId = event.getGuildId().orElse(null);
        Mono<User> author = event.getMessage().map(Message::getAuthor).flatMap(Mono::justOrEmpty);
        if(guildId == null){
            return Mono.empty();
        }

        Mono<Context> initContext = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .map(guildConfig -> Context.of(KEY_LOCALE, guildConfig.locale(),
                        KEY_TIMEZONE, guildConfig.timeZone()));

        Mono<StarboardConfig> starboardConfig = entityRetriever.getStarboardConfigById(guildId);

        Function<List<ReactionEmoji>, Mono<Integer>> emojisCount = emojis -> event.getMessage()
                .flatMapMany(message -> Flux.fromIterable(message.getReactions()))
                .filter(reaction -> emojis.contains(reaction.getEmoji()))
                .map(Reaction::getCount)
                .as(MathFlux::max);

        Mono<Starboard> starboard = entityRetriever.getStarboardById(guildId, event.getMessageId());

        return initContext.zipWith(starboardConfig).zipWhen(tuple -> Mono.just(tuple.getT2().emojis().stream()
                        .map(DiscordUtil::toReactionEmoji)
                        .collect(Collectors.toList())),
                (tuple, emojis) -> Tuples.of(tuple.getT1(), tuple.getT2(), emojis))
                .flatMap(function((context, config, emojis) -> emojisCount.apply(emojis).flatMap(l -> {
                    Snowflake channelId = config.starboardChannelId().orElse(null);
                    if(!config.isEnabled() || channelId == null || !emojis.contains(emoji) || l < config.lowerStarBarrier()){
                        return Mono.empty();
                    }

                    List<String> formatted = emojis.stream()
                            .map(DiscordUtil::getEmojiString)
                            .collect(Collectors.toList());

                    Mono<GuildMessageChannel> starboardChannel = event.getGuild()
                            .flatMap(guild -> guild.getChannelById(channelId))
                            .cast(GuildMessageChannel.class);

                    Mono<Message> targetMessage = starboard.zipWith(starboardChannel)
                            .flatMap(function((board, channel) -> channel.getMessageById(board.targetMessageId())));

                    Mono<Void> updateOld = event.getMessage().zipWith(targetMessage)
                            .flatMap(function((source, target) -> target.edit(spec -> spec.setEmbed(embed -> embed.from(target.getEmbeds().get(0).getData())
                                    .setColor(lerp(offsetColor, targetColor, Mathf.round(l / 6f, lerpStep))))
                                    .setContent(messageService.format(context, "starboard.format",
                                            formatted.get(Mathf.clamp((l - 1) / 5, 0, formatted.size() - 1)),
                                            l, DiscordUtil.getChannelMention(source.getChannelId()))))))
                            .then();

                    Mono<Starboard> findIfAbsent = Mono.zip(starboardChannel, event.getMessage(), author)
                            .flatMap(function((channel, source, user) -> channel.getMessagesBefore(Snowflake.of(Instant.now()))
                            .filter(m -> m.getEmbeds().size() == 1 && m.getEmbeds().get(0).getFields().size() >= 1)
                            .filter(m -> m.getEmbeds().get(0).getFields().get(0)
                                    .getValue().endsWith(source.getId().asString() + ")")) // match md link
                            .next()
                            .flatMap(target -> entityRetriever.createStarboard(guildId, source.getId(), target.getId()))));

                    Mono<Starboard> createNew = Mono.zip(starboardChannel, event.getMessage(), author).flatMap(function((channel, source, user) ->
                            channel.createMessage(spec -> spec.setContent(messageService.format(
                                    context, "starboard.format", formatted.get(Mathf.clamp((l - 1) / 5, 0, formatted.size() - 1)),
                                    l, DiscordUtil.getChannelMention(source.getChannelId())))
                                    .setAllowedMentions(AllowedMentions.suppressAll())
                                    .setEmbed(embed -> {
                                        embed.setFooter(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
                                                .withLocale(context.get(KEY_LOCALE))
                                                .withZone(context.get(KEY_TIMEZONE))
                                                .format(LocalDateTime.now()), null);
                                        embed.setAuthor(user.getUsername(), null, user.getAvatarUrl());
                                        embed.setDescription(source.getContent());
                                        embed.setColor(lerp(offsetColor, targetColor, Mathf.round(l / 6f, lerpStep)));
                                        embed.addField(messageService.get(context, "starboard.source"), messageService.format(context, "starboard.jump",
                                                guildId.asString(), source.getChannelId().asString(), source.getId().asString()), false);
                                        Set<Attachment> files = source.getAttachments().stream()
                                                .filter(att -> !att.getContentType().map(str -> str.startsWith("image")).orElse(false))
                                                .collect(Collectors.toSet());

                                        if(files.size() != 0){
                                            String key = files.size() == 1 ? "starboard.attachment" : "starboard.attachments";
                                            embed.addField(messageService.get(context, key), files.stream()
                                                    .map(att -> String.format("[%s](%s)", att.getFilename(), att.getUrl()))
                                                    .collect(Collectors.joining("\n")), false);
                                        }

                                        source.getAttachments().stream()
                                                .filter(att -> att.getContentType().map(str -> str.startsWith("image")).orElse(false))
                                                .map(Attachment::getUrl)
                                                .findFirst().ifPresent(embed::setImage);
                                    }))
                                    .flatMap(target -> entityRetriever.createStarboard(guildId, source.getId(), target.getId()))));

                    return not(starboard.hasElement()).flatMap(bool -> bool ? findIfAbsent.switchIfEmpty(createNew) : updateOld);
                }).contextWrite(context)));
    }

    @Override
    public Publisher<?> onReactionRemove(ReactionRemoveEvent event){
        ReactionEmoji emoji = event.getEmoji();
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        Mono<Context> initContext = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .map(guildConfig -> Context.of(KEY_LOCALE, guildConfig.locale(),
                        KEY_TIMEZONE, guildConfig.timeZone()));

        Mono<StarboardConfig> starboardConfig = entityRetriever.getStarboardConfigById(guildId);

        Function<List<ReactionEmoji>, Mono<Integer>> emojisCount = emojis -> event.getMessage()
                .flatMapMany(message -> Flux.fromIterable(message.getReactions()))
                .filter(reaction -> emojis.contains(reaction.getEmoji()))
                .map(Reaction::getCount)
                .as(MathFlux::max)
                .defaultIfEmpty(0);

        return initContext.zipWith(starboardConfig).zipWhen(tuple -> Mono.just(tuple.getT2().emojis().stream()
                        .map(DiscordUtil::toReactionEmoji)
                        .collect(Collectors.toList())),
                (tuple, emojis) -> Tuples.of(tuple.getT1(), tuple.getT2(), emojis))
                .flatMap(function((context, config, emojis) -> emojisCount.apply(emojis).flatMap(l -> {
                    Snowflake channelId = config.starboardChannelId().orElse(null);
                    if(!config.isEnabled() || channelId == null || !emojis.contains(emoji)){
                        return Mono.empty();
                    }

                    List<String> formatted = emojis.stream()
                            .map(DiscordUtil::getEmojiString)
                            .collect(Collectors.toList());

                    Mono<GuildMessageChannel> starboardChannel = event.getGuild().flatMap(guild -> guild.getChannelById(channelId))
                            .cast(GuildMessageChannel.class);

                    Mono<Starboard> findIfAbsent = Mono.zip(starboardChannel, event.getMessage())
                            .flatMap(function((channel, source) -> channel.getMessagesBefore(Snowflake.of(Instant.now()))
                            .filter(m -> m.getEmbeds().size() == 1 && m.getEmbeds().get(0).getFields().size() >= 1)
                            .filter(m -> m.getEmbeds().get(0).getFields().get(0)
                                    .getValue().endsWith(source.getId().asString() + ")")) // match md link
                            .next()
                            .flatMap(target -> entityRetriever.createStarboard(guildId, source.getId(), target.getId()))));

                    Mono<Starboard> starboard = entityRetriever.getStarboardById(guildId, event.getMessageId())
                            .switchIfEmpty(findIfAbsent);

                    return Mono.zip(starboard, starboardChannel)
                            .zipWhen(tuple -> tuple.getT2().getMessageById(tuple.getT1().targetMessageId()),
                                    (tuple, message) -> Tuples.of(tuple.getT1(), tuple.getT2(), message))
                            .flatMap(function((board, channel, target) -> {
                                if(l < config.lowerStarBarrier()){
                                    return target.delete().and(entityRetriever.delete(board));
                                }

                                Snowflake sourceChannelId = event.getChannelId();
                                return target.edit(spec -> spec.setEmbed(embed -> embed.from(target.getEmbeds().get(0).getData())
                                        .setColor(lerp(offsetColor, targetColor, Mathf.round(l / 6f, lerpStep))))
                                        .setContent(messageService.format(context, "starboard.format",
                                                formatted.get(Mathf.clamp((l - 1) / 5, 0, formatted.size() - 1)),
                                        l, DiscordUtil.getChannelMention(sourceChannelId))));
                    }));
        }).contextWrite(context)));
    }

    @Override
    public Publisher<?> onReactionRemoveAll(ReactionRemoveAllEvent event){
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        Mono<StarboardConfig> starboardConfig = entityRetriever.getStarboardConfigById(guildId);

        Mono<Starboard> starboard = entityRetriever.getStarboardById(guildId, event.getMessageId());

        return starboardConfig.flatMap(config -> {
            Snowflake channelId = config.starboardChannelId().orElse(null);
            if(!config.isEnabled() || channelId == null){
                return Mono.empty();
            }

            return starboard.flatMap(board -> event.getGuild().flatMap(guild -> guild.getChannelById(channelId))
                    .cast(GuildMessageChannel.class)
                    .flatMap(channel -> channel.getMessageById(board.targetMessageId()))
                    .flatMap(Message::delete)
                    .then(entityRetriever.delete(board)));
        });
    }

    @Override
    public Publisher<?> onMessageDelete(MessageDeleteEvent event){
        return Mono.justOrEmpty(event.getGuildId()).flatMap(guildId ->
                entityRetriever.deleteStarboardById(guildId, event.getMessageId()));
    }

    private Color lerp(Color source, Color target, float t){
        float r = source.getRed()/255f;
        r += t * (target.getRed()/255f - r);

        float g = source.getGreen()/255f;
        g += t * (target.getGreen()/255f - g);

        float b = source.getBlue()/255f;
        b += t * (target.getBlue()/255f - b);

        Color c = Color.of(Mathf.clamp(r), Mathf.clamp(g), Mathf.clamp(b));
        return toIntBits(c) < toIntBits(target) ? target : c;
    }

    private int toIntBits(Color c){
        return (int)(255f * c.getBlue()) << 16 | (int)(255f * c.getGreen()) << 8 | (int)(255f * c.getRed());
    }
}
