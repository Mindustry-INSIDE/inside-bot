package inside.service;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.*;
import inside.command.model.CommandEnvironment;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.*;
import java.util.function.Consumer;

public interface MessageService{

    ReactionEmoji ok = ReactionEmoji.unicode("✅");

    ReactionEmoji failed = ReactionEmoji.unicode("❌");

    String placeholder = ":eyes: (?)";

    // bundle

    String get(ContextView ctx, String key);

    String getCount(ContextView ctx, String key, long count);

    String getEnum(ContextView ctx, Enum<?> type);

    String format(ContextView ctx, String key, Object... args);

    Optional<Locale> getLocale(String str);

    Map<String, Locale> getSupportedLocales();

    Locale getDefaultLocale();

    // send (command)

    Mono<Void> text(CommandEnvironment environment, String text, Object... args);

    Mono<Void> text(CommandEnvironment environment, Consumer<MessageCreateSpec> message);

    Mono<Void> info(Mono<? extends MessageChannel> channel, String title, String text, Object... args); // for using in DM

    Mono<Void> info(CommandEnvironment environment, String title, String text, Object... args);

    Mono<Void> info(CommandEnvironment environment, Consumer<EmbedCreateSpec> embed);

    Mono<Void> err(CommandEnvironment environment, String text, Object... args);

    Mono<Void> error(CommandEnvironment environment, String title, String text, Object... args);

    // send (interactive)

    Mono<Void> text(InteractionCreateEvent event, String text, Object... args);

    Mono<Void> info(InteractionCreateEvent event, String title, String text, Object... args);

    Mono<Void> info(InteractionCreateEvent event, Consumer<EmbedCreateSpec> embed);

    Mono<Void> err(InteractionCreateEvent event, String text, Object... args);

    Mono<Void> error(InteractionCreateEvent event, String title, String text, Object... args);

    // data

    String encrypt(String text, Snowflake messageId, Snowflake channelId);

    String decrypt(String text, Snowflake messageId, Snowflake channelId);

    // caching

    void awaitEdit(Snowflake messageId);

    void removeEdit(Snowflake messageId);

    boolean isAwaitEdit(Snowflake messageId);
}
