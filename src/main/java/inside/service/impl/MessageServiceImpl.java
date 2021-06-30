package inside.service.impl;

import com.github.benmanes.caffeine.cache.*;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.*;
import discord4j.rest.util.AllowedMentions;
import inside.Settings;
import inside.command.model.CommandEnvironment;
import inside.service.MessageService;
import inside.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static inside.util.ContextUtil.*;

@Service
public class MessageServiceImpl implements MessageService{

    private static final Map<String, Locale> locales;

    private static final Map<String, Map<String, Pattern>> pluralRules;

    private static final String ruLocale = "ru";
    private static final String defaultLocale = "en";

    static{
        locales = Map.of(
                ruLocale, new Locale(ruLocale),
                defaultLocale, new Locale(defaultLocale)
        );

        pluralRules = Map.of(
                ruLocale, Map.of(
                        "zero", Pattern.compile("^\\d*0$"),
                        "one", Pattern.compile("^(-?\\d*[^1])?1$"),
                        "two", Pattern.compile("^(-?\\d*[^1])?2$"),
                        "few", Pattern.compile("(^(-?\\d*[^1])?3)|(^(-?\\d*[^1])?4)$"),
                        "many", Pattern.compile("^\\d+$")
                ),
                defaultLocale, Map.of(
                        "zero", Pattern.compile("^0$"),
                        "one", Pattern.compile("^1$"),
                        "other", Pattern.compile("^\\d+$")
                )
        );
    }

    private final ApplicationContext context;

    private final Settings settings;

    private final Cache<Snowflake, Boolean> waitingMessage = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    public MessageServiceImpl(@Autowired ApplicationContext context,
                              @Autowired Settings settings){
        this.context = context;
        this.settings = settings;
    }

    @Override
    public String get(ContextView ctx, String key){
        try{
            return Strings.isEmpty(key) ? "" : context.getMessage(key, null, ctx.get(KEY_LOCALE));
        }catch(NoSuchMessageException e){
            return key;
        }
    }

    @Override
    public String getCount(ContextView ctx, String key, long count){
        String code = getCount0(ctx.get(KEY_LOCALE), count);
        return get(ctx, String.format("%s.%s", key, code));
    }

    private String getCount0(Locale locale, long value){
        String str = String.valueOf(value);
        Map<String, Pattern> rules = pluralRules.getOrDefault(locale.getLanguage(), pluralRules.get(defaultLocale));
        return rules.entrySet().stream()
                .filter(plural -> plural.getValue().matcher(str).find())
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse("other");
    }

    @Override
    public String getEnum(ContextView ctx, Enum<?> type){
        return get(ctx, String.format("%s.%s", type.getClass().getCanonicalName(), type.name()));
    }

    @Override
    public String format(ContextView ctx, String key, Object... args){
        try{
            return context.getMessage(key, args, ctx.get(KEY_LOCALE));
        }catch(NoSuchMessageException e){
            return key;
        }
    }

    @Override
    public Optional<Locale> getLocale(String str){
        return Optional.ofNullable(locales.get(str));
    }

    @Override
    public Map<String, Locale> getSupportedLocales(){
        return locales;
    }

    @Override
    public Locale getDefaultLocale(){
        return locales.get(defaultLocale);
    }

    @Override
    public Mono<Void> text(CommandEnvironment environment, String text, Object... args){
        return Mono.deferContextual(ctx -> environment.getReplyChannel().publishOn(Schedulers.boundedElastic())
                .flatMap(c -> c.createMessage(spec -> {
                    if(ctx.<Boolean>getOrEmpty(KEY_REPLY).isPresent()){
                        spec.setMessageReference(environment.getMessage().getId());
                    }
                    spec.setContent(text.isBlank() ? placeholder : format(ctx, text, args));
                    spec.setAllowedMentions(AllowedMentions.suppressAll());
                }))
                .then());
    }

    @Override
    public Mono<Void> text(CommandEnvironment environment, Consumer<MessageCreateSpec> message){
        return Mono.deferContextual(ctx -> environment.getReplyChannel()
                .flatMap(c -> c.createMessage(message.andThen(spec -> {
                    if(ctx.<Boolean>getOrEmpty(KEY_REPLY).isPresent()){
                        spec.setMessageReference(environment.getMessage().getId());
                    }
                    spec.setAllowedMentions(AllowedMentions.suppressAll());
                })))
                .then());
    }

    @Override
    public Mono<Void> info(Mono<? extends MessageChannel> channel, String title, String text, Object... args){
        return Mono.deferContextual(ctx -> channel.flatMap(c -> c.createMessage(spec -> spec.setAllowedMentions(AllowedMentions.suppressAll())
                .setEmbed(embed -> embed.setTitle(get(ctx, title))
                        .setDescription(format(ctx, text, args))
                        .setColor(settings.getDefaults().getNormalColor()))))
                .then());
    }

    @Override
    public Mono<Void> info(CommandEnvironment environment, String title, String text, Object... args){
        return Mono.deferContextual(ctx -> info(environment, embed -> embed.setTitle(get(ctx, title))
                .setDescription(format(ctx, text, args))));
    }

    @Override
    public Mono<Void> info(CommandEnvironment environment, Consumer<EmbedCreateSpec> embed){
        return Mono.deferContextual(ctx -> environment.getReplyChannel()
                .flatMap(c -> c.createMessage(spec -> {
                    if(ctx.<Boolean>getOrEmpty(KEY_REPLY).isPresent()){
                        spec.setMessageReference(environment.getMessage().getId());
                    }

                    spec.setAllowedMentions(AllowedMentions.suppressAll());
                    spec.setEmbed(embed.andThen(after -> after.setColor(settings.getDefaults().getNormalColor())));
                })).then());
    }

    @Override
    public Mono<Void> err(CommandEnvironment environment, String text, Object... args){
        return error(environment, "message.error.general.title", text, args);
    }

    @Override
    public Mono<Void> error(CommandEnvironment environment, String title, String text, Object... args){
        return Mono.deferContextual(ctx -> environment.getReplyChannel()
                .flatMap(c -> c.createMessage(spec -> {
                    if(ctx.<Boolean>getOrEmpty(KEY_REPLY).isPresent()){
                        spec.setMessageReference(environment.getMessage().getId());
                    }

                    spec.setAllowedMentions(AllowedMentions.suppressAll());
                    spec.setEmbed(embed -> embed.setColor(settings.getDefaults().getErrorColor())
                            .setDescription(format(ctx, text, args))
                            .setTitle(get(ctx, title)));
                }))
                .flatMap(message -> Mono.delay(settings.getDiscord().getErrorEmbedTtl())
                        .then(message.delete().and(environment.getMessage().addReaction(failed)))));
    }

    @Override
    public Mono<Void> text(InteractionCreateEvent event, String text, Object... args){
        return Mono.deferContextual(ctx -> event.reply(spec -> spec.setEphemeral(ctx.<Boolean>getOrEmpty(KEY_EPHEMERAL).orElse(false))
                .setAllowedMentions(AllowedMentions.suppressAll())
                .setContent(text.isBlank() ? placeholder : format(ctx, text, args))));
    }

    @Override
    public Mono<Void> info(InteractionCreateEvent event, String title, String text, Object... args){
        return Mono.deferContextual(ctx -> info(event, embed -> embed.setTitle(get(ctx, title))
                .setDescription(format(ctx, text, args))));
    }

    @Override
    public Mono<Void> info(InteractionCreateEvent event, Consumer<EmbedCreateSpec> embed){
        return event.reply(spec -> spec.addEmbed(embed.andThen(after ->
                after.setColor(settings.getDefaults().getNormalColor()))));
    }

    @Override
    public Mono<Void> err(InteractionCreateEvent event, String text, Object... args){
        return error(event, "message.error.general.title", text, args);
    }

    @Override
    public Mono<Void> error(InteractionCreateEvent event, String title, String text, Object... args){
        return Mono.deferContextual(ctx -> event.reply(spec -> spec.setAllowedMentions(AllowedMentions.suppressAll())
                .addEmbed(embed -> embed.setColor(settings.getDefaults().getErrorColor())
                        .setDescription(format(ctx, text, args))
                        .setTitle(get(ctx, title)))));
    }

    @Override
    public void awaitEdit(Snowflake messageId){
        waitingMessage.put(messageId, true);
    }

    @Override
    public void removeEdit(Snowflake messageId){
        waitingMessage.invalidate(messageId);
    }

    @Override
    public boolean isAwaitEdit(Snowflake messageId){
        return Boolean.TRUE.equals(waitingMessage.getIfPresent(messageId));
    }

    @Override
    public String encrypt(String text, Snowflake messageId, Snowflake channelId){
        if(settings.getDiscord().isEncryptMessages()){
            String password = messageId.asString();
            String salt = channelId.asString();
            AesEncryptor coder = new AesEncryptor(password, salt);
            return coder.encrypt(text);
        }
        return text;
    }

    @Override
    public String decrypt(String text, Snowflake messageId, Snowflake channelId){
        if(settings.getDiscord().isEncryptMessages()){
            String password = messageId.asString();
            String salt = channelId.asString();
            AesEncryptor coder = new AesEncryptor(password, salt);
            return coder.decrypt(text);
        }
        return text;
    }
}
