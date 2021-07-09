package inside.event;

import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.reaction.*;
import discord4j.core.spec.*;
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

                    Mono<Message> findIfAbsent = Mono.zip(starboardChannel, event.getMessage(), author)
                            .flatMap(function((channel, source, user) -> channel.getLastMessageId()
                            .map(channel::getMessagesBefore).orElse(Flux.empty())
                            .take(Duration.ofSeconds(4))
                            .filter(m -> m.getEmbeds().size() == 1 && m.getEmbeds().get(0).getFields().size() >= 1)
                            .filter(m -> m.getEmbeds().get(0).getFields().get(0)
                                    .getValue().endsWith(source.getId().asString() + ")")) // match md link
                            .next()
                            .flatMap(target -> entityRetriever.createStarboard(guildId, source.getId(), target.getId())
                                    .thenReturn(target))))
                            .timeout(Duration.ofSeconds(6), Mono.empty());

                    Mono<Message> targetMessage = starboard.zipWith(starboardChannel)
                            .flatMap(function((board, channel) -> channel.getMessageById(board.targetMessageId())))
                            .switchIfEmpty(findIfAbsent);

                    Mono<Message> updateOld = event.getMessage().zipWith(targetMessage)
                            .flatMap(function((source, target) -> target.edit(MessageEditSpec.builder()
                                    .embedOrNull(EmbedCreateSpec.builder()
                                            .color(lerp(offsetColor, targetColor, Mathf.round(l / 6f, lerpStep)))
                                            .build())
                                    .contentOrNull(messageService.format(context, "starboard.format",
                                            formatted.get(Mathf.clamp((l - 1) / 5, 0, formatted.size() - 1)),
                                            l, DiscordUtil.getChannelMention(source.getChannelId())))
                                    .build())));

                    Mono<Message> createNew = Mono.zip(starboardChannel, event.getMessage(), author).flatMap(function((channel, source, user) -> {
                        var embedSpec = EmbedCreateSpec.builder();

                        embedSpec.footer(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
                                .withLocale(context.get(KEY_LOCALE))
                                .withZone(context.get(KEY_TIMEZONE))
                                .format(Instant.now()), null);
                        embedSpec.author(user.getUsername(), null, user.getAvatarUrl());
                        embedSpec.description(source.getContent());
                        embedSpec.color(lerp(offsetColor, targetColor, Mathf.round(l / 6f, lerpStep)));
                        embedSpec.addField(messageService.get(context, "starboard.source"), messageService.format(context, "starboard.jump",
                                guildId.asString(), source.getChannelId().asString(), source.getId().asString()), false);
                        Set<Attachment> files = source.getAttachments().stream()
                                .filter(att -> !att.getContentType().map(str -> str.startsWith("image")).orElse(false))
                                .collect(Collectors.toSet());

                        if(files.size() != 0){
                            String key = files.size() == 1 ? "starboard.attachment" : "starboard.attachments";
                            embedSpec.addField(messageService.get(context, key), files.stream()
                                    .map(att -> String.format("[%s](%s)%n", att.getFilename(), att.getUrl()))
                                    .collect(Collectors.joining()), false);
                        }

                        source.getAttachments().stream()
                                .filter(att -> att.getContentType().map(str -> str.startsWith("image")).orElse(false))
                                .map(Attachment::getUrl)
                                .findFirst().ifPresent(embedSpec::image);

                        return channel.createMessage(MessageCreateSpec.builder()
                                .content(messageService.format(
                                        context, "starboard.format", formatted.get(Mathf.clamp((l - 1) / 5, 0, formatted.size() - 1)),
                                        l, DiscordUtil.getChannelMention(source.getChannelId())))
                                .allowedMentions(AllowedMentions.suppressAll())
                                .embed(embedSpec.build())
                                .build())
                                .flatMap(target -> entityRetriever.createStarboard(guildId, source.getId(), target.getId())
                                        .thenReturn(target));
                    }));

                    return updateOld.switchIfEmpty(createNew);
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

                    Mono<Message> findIfAbsent = Mono.zip(starboardChannel, event.getMessage())
                            .flatMap(function((channel, source) -> channel.getLastMessageId()
                                    .map(channel::getMessagesBefore).orElse(Flux.empty())
                            .take(Duration.ofSeconds(4))
                            .filter(m -> m.getEmbeds().size() == 1 && m.getEmbeds().get(0).getFields().size() >= 1)
                            .filter(m -> m.getEmbeds().get(0).getFields().get(0)
                                    .getValue().endsWith(source.getId().asString() + ")")) // match md link
                            .next()
                            .flatMap(target -> entityRetriever.createStarboard(guildId, source.getId(), target.getId())
                                    .thenReturn(target))))
                            .timeout(Duration.ofSeconds(6), Mono.empty());

                    Mono<Starboard> starboard = entityRetriever.getStarboardById(guildId, event.getMessageId());

                    Mono<Message> targetMessage = starboard.zipWith(starboardChannel)
                            .flatMap(function((board, channel) -> channel.getMessageById(board.targetMessageId())))
                            .switchIfEmpty(findIfAbsent);

                    return starboardChannel.zipWith(targetMessage)
                            .flatMap(function((channel, target) -> {
                                if(l < config.lowerStarBarrier()){
                                    return target.delete().and(entityRetriever.deleteStarboardById(guildId, event.getMessageId()));
                                }

                                Snowflake sourceChannelId = event.getChannelId();
                                return target.edit(MessageEditSpec.builder()
                                        .embedOrNull(EmbedCreateSpec.builder()
                                                .color(lerp(offsetColor, targetColor, Mathf.round(l / 6f, lerpStep)))
                                                .build())
                                        .contentOrNull(messageService.format(context, "starboard.format",
                                                formatted.get(Mathf.clamp((l - 1) / 5, 0, formatted.size() - 1)),
                                                l, DiscordUtil.getChannelMention(sourceChannelId)))
                                        .build());
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
