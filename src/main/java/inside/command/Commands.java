package inside.command;

import com.udojava.evalex.*;
import discord4j.common.ReactorResources;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.presence.Presence;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.discordjson.json.*;
import discord4j.rest.util.Permission;
import inside.Settings;
import inside.command.model.*;
import inside.data.entity.*;
import inside.data.service.AdminService;
import inside.event.audit.*;
import inside.util.*;
import io.netty.handler.codec.http.*;
import org.joda.time.*;
import org.joda.time.format.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import reactor.bool.BooleanUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.*;
import reactor.function.TupleUtils;
import reactor.netty.http.client.HttpClient;
import reactor.util.*;
import reactor.util.annotation.Nullable;
import reactor.util.function.Tuple2;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static inside.command.Commands.TranslitCommand.*;
import static inside.event.audit.Attribute.COUNT;
import static inside.event.audit.BaseAuditProvider.MESSAGE_TXT;
import static inside.service.MessageService.ok;
import static inside.util.ContextUtil.*;

public class Commands{

    private Commands(){}

    public static abstract class ModeratorCommand extends Command{
        @Lazy
        @Autowired
        protected AdminService adminService;

        @Override
        public Mono<Boolean> apply(CommandRequest req){
            return adminService.isAdmin(req.getAuthorAsMember());
        }
    }

    public static abstract class TestCommand extends Command{
        @Override
        public Mono<Boolean> apply(CommandRequest req){
            return req.getClient().getApplicationInfo()
                    .map(ApplicationInfo::getOwnerId)
                    .map(owner -> owner.equals(req.getAuthorAsMember().getId()));
        }
    }

    @DiscordCommand(key = "help", description = "command.help.description")
    public static class HelpCommand extends Command{
        @Autowired
        private CommandHandler handler;

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Snowflake guildId = env.getAuthorAsMember().getGuildId();
            String prefix = entityRetriever.getPrefix(guildId);

            Collector<CommandInfo, StringBuilder, StringBuilder> collector = Collector.of(StringBuilder::new,
                    (builder, commandInfo) -> {
                        builder.append(prefix);
                        builder.append("**");
                        builder.append(commandInfo.text());
                        builder.append("**");
                        if(commandInfo.params().length > 0){
                            builder.append(" *");
                            builder.append(messageService.get(env.context(), commandInfo.paramText()));
                            builder.append("*");
                        }
                        builder.append(" - ");
                        builder.append(messageService.get(env.context(), commandInfo.description()));
                        builder.append("\n");
                    },
                    StringBuilder::append);

            return Flux.fromIterable(handler.commandList())
                    .filterWhen(commandInfo -> handler.commands().get(commandInfo.text()).apply(env))
                    .collect(collector)
                    .map(builder -> builder.append(messageService.get(env.context(), "command.help.disclaimer.user"))
                            .append("\n")
                            .append(messageService.format(env.context(), "command.help.disclaimer.help", prefix)))
                    .flatMap(builder -> messageService.info(env.getReplyChannel(),"command.help", builder.toString()));
        }
    }

    @DiscordCommand(key = "ping", description = "command.ping.description")
    public static class PingCommand extends Command{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            long start = System.currentTimeMillis();
            return env.getReplyChannel()
                    .flatMap(channel -> channel.createMessage(
                            messageService.get(env.context(), "command.ping.testing")))
                    .flatMap(message -> message.edit(spec -> spec.setContent(
                            messageService.format(env.context(), "command.ping.completed",
                                    System.currentTimeMillis() - start))))
                    .then();
        }
    }

    @DiscordCommand(key = "base64", params = "command.base64.params", description = "command.base64.description")
    public static class Base64Command extends Command{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            boolean encode = args[0].matches("(?i)enc(ode)?");
            Mono<String> result = Mono.fromCallable(() ->
                    encode ? Base64Coder.encodeString(args[1]) : Base64Coder.decodeString(args[1]));

            return result.onErrorResume(t -> t instanceof IllegalArgumentException,
                    t -> messageService.err(env.getReplyChannel(), t.getMessage()).then(Mono.empty()))
                    .flatMap(str -> messageService.text(env.getReplyChannel(),
                            MessageUtil.substringTo(str, Message.MAX_CONTENT_LENGTH)));
        }

        @Override
        public Mono<Void> help(CommandEnvironment env){
            String prefix = entityRetriever.getPrefix(env.getAuthorAsMember().getGuildId());
            return messageService.info(env.getReplyChannel(), "command.help.title", "command.base64.help", prefix);
        }
    }

    @DiscordCommand(key = "avatar", params = "command.avatar.params", description = "command.avatar.description")
    public static class AvatarCommand extends Command{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Mono<MessageChannel> channel = env.getReplyChannel();
            Snowflake targetId = args.length > 0 ? MessageUtil.parseUserId(args[0]) : env.getAuthorAsMember().getId();

            return Mono.justOrEmpty(targetId).flatMap(id -> env.getClient().withRetrievalStrategy(EntityRetrievalStrategy.REST).getUserById(id))
                    .switchIfEmpty(messageService.err(channel, "command.incorrect-name").then(Mono.empty()))
                    .flatMap(user -> messageService.info(channel, embed -> embed.setImage(user.getAvatarUrl() + "?size=512")
                            .setDescription(messageService.format(env.context(), "command.avatar.text", user.getUsername()))));
        }
    }

    @DiscordCommand(key = "math", params = "command.math.params", description = "command.math.description")
    public static class MathCommand extends Command{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Mono<BigDecimal> result = Mono.fromCallable(() -> {
                Expression exp = new Expression(args[0]).setPrecision(10);
                exp.addOperator(shiftRightOperator);
                exp.addOperator(shiftLeftOperator);
                return exp.eval();
            });

            return result.onErrorResume(t -> t instanceof ArithmeticException || t instanceof Expression.ExpressionException,
                    t -> messageService.error(env.getReplyChannel(), "command.math.error.title", t.getMessage()).then(Mono.empty()))
                    .flatMap(decimal -> messageService.text(env.getReplyChannel(),
                            MessageUtil.substringTo(decimal.toString(), Message.MAX_CONTENT_LENGTH)));
        }

        @Override
        public Mono<Void> help(CommandEnvironment env){
            String prefix = entityRetriever.getPrefix(env.getAuthorAsMember().getGuildId());
            return messageService.info(env.getReplyChannel(), "command.help.title", "command.math.help", prefix);
        }

        private static final LazyOperator shiftRightOperator = new AbstractOperator(">>", 30, true){
            @Override
            public BigDecimal eval(BigDecimal v1, BigDecimal v2){
                return v1.movePointRight(v2.toBigInteger().intValue());
            }
        };

        private static final LazyOperator shiftLeftOperator = new AbstractOperator("<<", 30, true){
            @Override
            public BigDecimal eval(BigDecimal v1, BigDecimal v2){
                return v1.movePointLeft(v2.toBigInteger().intValue());
            }
        };
    }

    @DiscordCommand(key = "read", params = "command.read.params", description = "command.read.description")
    public static class ReadCommand extends Command{
        private static final Logger log = Loggers.getLogger(ReadCommand.class);

        private static final String IP_PATTERN = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

        private static final int MAX_SIZE = 3 * 1024 * 1024; // 3mb

        private final HttpClient httpClient = ReactorResources.DEFAULT_HTTP_CLIENT.get();

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            return Mono.just(args[0]).flatMap(url -> httpClient.get()
                    .uri(url)
                    .responseSingle((res, mono) -> {
                        String type = res.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE).toLowerCase();
                        long size = Strings.parseLong(res.responseHeaders().get(HttpHeaderNames.CONTENT_LENGTH));
                        if(res.status().equals(HttpResponseStatus.OK) && !type.contains("audio") && !type.contains("image")
                                && !type.contains("video") && size != Long.MIN_VALUE){
                            return size > MAX_SIZE ?
                                   messageService.err(env.getReplyChannel(), "command.read.under-limit").then(Mono.never()) :
                                   mono.asString(Strings.utf8);
                        }
                        return Mono.empty();
                    }))
                    .onErrorResume(t -> true, t -> Mono.fromRunnable(() -> log.debug("Failed to request file.", t)))
                    .switchIfEmpty(messageService.err(env.getReplyChannel(), "command.read.error").then(Mono.empty()))
                    .map(content -> content.replaceAll(IP_PATTERN,
                            messageService.get(env.context(), "command.read.ip-address")))
                    .map(content -> MessageUtil.substringTo(content, Message.MAX_CONTENT_LENGTH))
                    .flatMap(content -> messageService.text(env.getReplyChannel(), content));
        }
    }

    @DiscordCommand(key = "status", params = "command.status.params", description = "command.status.description")
    public static class StatusCommand extends TestCommand{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            return switch(args[0].toLowerCase()){
                case "online" -> env.getClient().updatePresence(Presence.online());
                case "dnd" -> env.getClient().updatePresence(Presence.doNotDisturb());
                case "idle" -> env.getClient().updatePresence(Presence.idle());
                case "invisible" -> env.getClient().updatePresence(Presence.invisible());
                default -> messageService.err(env.getReplyChannel(), "command.status.unknown-presence");
            };
        }
    }

    @DiscordCommand(key = "r", params = "command.text-layout.params", description = "command.text-layout.description")
    public static class TextLayoutCommand extends Command{
        private static final String[] latPattern;
        private static final String[] rusPattern;

        static{
            String lat = "Q-W-E-R-T-Y-U-I-O-P-A-S-D-F-G-H-J-K-L-Z-X-C-V-B-N-M";
            String rus = "Й-Ц-У-К-Е-Н-Г-Ш-Щ-З-Ф-Ы-В-А-П-Р-О-Л-Д-Я-Ч-С-М-И-Т-Ь";
            latPattern = (lat + "-" + lat.toLowerCase() + "-\\^-:-\\$-@-&-~-`-\\{-\\[-\\}-\\]-\"-'-<->-;-\\?-\\/-\\.-,-#").split("-");
            rusPattern = (rus + "-" + rus.toLowerCase() + "-:-Ж-;-\"-\\?-Ё-ё-Х-х-Ъ-ъ-Э-э-Б-Ю-ж-,-\\.-ю-б-№").split("-");
        }

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            boolean lat = args[0].equalsIgnoreCase("lat");
            return messageService.text(env.getReplyChannel(), lat ? text2rus(args[1]) : text2lat(args[1]));
        }

        public String text2rus(String text){
            for(int i = 0; i < latPattern.length; i++){
                text = text.replaceAll("(?u)" + latPattern[i], rusPattern[i]);
            }
            return text;
        }

        public String text2lat(String text){
            for(int i = 0; i < rusPattern.length; i++){
                text = text.replaceAll("(?u)" + rusPattern[i], latPattern[i]);
            }
            return text;
        }
    }

    @DiscordCommand(key = "1337", params = "command.1337.params", description = "command.1337.description")
    public static class LeetCommand extends Command{
        public static final Map<String, String> rusLeetSpeak;
        public static final Map<String, String> latLeetSpeak;

        static{
            rusLeetSpeak = of(
                    "а", "4", "б", "6", "в", "8", "г", "g",
                    "д", "d", "е", "3", "ё", "3", "ж", "zh",
                    "з", "e", "и", "i", "й", "\\`i", "к", "k",
                    "л", "l", "м", "m", "н", "n", "о", "0",
                    "п", "p", "р", "r", "с", "c", "т", "7",
                    "у", "y", "ф", "f", "х", "x", "ц", "u,",
                    "ч", "ch", "ш", "w", "щ", "w,", "ъ", "\\`ь",
                    "ы", "ьi", "ь", "ь", "э", "э", "ю", "10",
                    "я", "9"
            );

            latLeetSpeak = of(
                    "a", "4", "b", "8", "c", "c", "d", "d",
                    "e", "3", "f", "ph", "g", "9", "h", "h",
                    "i", "1", "j", "g", "k", "k", "l", "l",
                    "m", "m", "n", "n", "o", "0", "p", "p",
                    "q", "q", "r", "r", "s", "5", "t", "7",
                    "u", "u", "v", "v", "w", "w", "x", "x",
                    "y", "y", "z", "2"
            );
        }

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            boolean lat = args[0].equalsIgnoreCase("lat");
            return messageService.text(env.getReplyChannel(), MessageUtil.substringTo(leeted(args[1], lat), Message.MAX_CONTENT_LENGTH));
        }

        public String leeted(String text, boolean lat){
            Map<String, String> map = lat ? latLeetSpeak : rusLeetSpeak;
            UnaryOperator<String> get = s -> {
                String result = map.get(s.toLowerCase());
                if(result == null){
                    result = translit.keySet().stream().filter(s::equalsIgnoreCase).findFirst().orElse(null);
                }
                return result != null ? s.chars().anyMatch(Character::isUpperCase) ? result.toUpperCase() : result : "";
            };

            int len = text.length();
            if(len == 1){
                return get.apply(text);
            }

            StringBuilder result = new StringBuilder();
            for(int i = 0; i < len; ){
                String c = text.substring(i, i <= len - 2 ? i + 2 : i + 1);
                String leeted = get.apply(c);
                if(Strings.isEmpty(leeted)){
                    leeted = get.apply(c.charAt(0) + "");
                    result.append(Strings.isEmpty(leeted) ? c.charAt(0) : leeted);
                    i++;
                }else{
                    result.append(leeted);
                    i += 2;
                }
            }
            return result.toString();
        }
    }

    @DiscordCommand(key = "tr", params = "command.translit.params", description = "command.translit.description")
    public static class TranslitCommand extends Command{
        public static final Map<String, String> translit;

        static{
            translit = of(
                    "a", "а", "b", "б", "v", "в", "g", "г",
                    "d", "д", "e", "е", "yo", "ё", "zh", "ж",
                    "z", "з", "i", "и", "j", "й", "k", "к",
                    "l", "л", "m", "м", "n", "н", "o", "о",
                    "p", "п", "r", "р", "s", "с", "t", "т",
                    "u", "у", "f", "ф", "h", "х", "ts", "ц",
                    "ch", "ч", "sh", "ш", "\\`", "ъ", "y", "у",
                    "'", "ь", "yu", "ю", "ya", "я", "x", "кс",
                    "v", "в", "q", "к", "iy", "ий"
            );
        }

        @SuppressWarnings("unchecked")
        public static <K, V> Map<K, V> of(Object... values) {
            Map<K, V> map = new HashMap<>();

            for(int i = 0; i < values.length / 2; ++i) {
                map.put((K)values[i * 2], (V)values[i * 2 + 1]);
            }

            return Map.copyOf(map);
        }

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            return messageService.text(env.getReplyChannel(), MessageUtil.substringTo(translit(args[0]), Message.MAX_CONTENT_LENGTH));
        }

        public String translit(String text){
            UnaryOperator<String> get = s -> {
                String result = translit.get(s.toLowerCase());
                if(result == null){
                    result = translit.keySet().stream().filter(s::equalsIgnoreCase).findFirst().orElse(null);
                }
                return result != null ? s.chars().anyMatch(Character::isUpperCase) ? result.toUpperCase() : result : "";
            };

            int len = text.length();
            if(len == 1){
                return get.apply(text);
            }

            StringBuilder result = new StringBuilder();
            for(int i = 0; i < len; ){
                String c = text.substring(i, i <= len - 2 ? i + 2 : i + 1);
                String translited = get.apply(c);
                if(Strings.isEmpty(translited)){
                    translited = get.apply(c.charAt(0) + "");
                    result.append(Strings.isEmpty(translited) ? c.charAt(0) : translited);
                    i++;
                }else{
                    result.append(translited);
                    i += 2;
                }
            }
            return result.toString();
        }
    }

    @DiscordCommand(key = "prefix", params = "command.config.prefix.params", description = "command.config.prefix.description")
    public static class PrefixCommand extends Command{
        @Autowired
        private AdminService adminService;

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Member member = env.getAuthorAsMember();
            Mono<MessageChannel> channel = env.getReplyChannel();

            return Mono.justOrEmpty(entityRetriever.getGuildById(member.getGuildId()))
                    .filterWhen(guildConfig -> adminService.isOwner(member))
                    .switchIfEmpty(messageService.err(channel, "command.owner-only").then(Mono.empty()))
                    .flatMap(guildConfig -> {
                        if(args.length == 0){
                            return messageService.text(channel, "command.config.prefix", guildConfig.prefix());
                        }else{
                            if(!args[0].isBlank()){
                                guildConfig.prefix(args[0]);
                                entityRetriever.save(guildConfig);
                                return messageService.text(channel, "command.config.prefix-updated", guildConfig.prefix());
                            }
                        }

                        return Mono.empty();
                    });
        }
    }

    @DiscordCommand(key = "timezone", params = "command.config.timezone.params", description = "command.config.timezone.description")
    public static class TimezoneCommand extends Command{
        @Autowired
        private AdminService adminService;

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Member member = env.getAuthorAsMember();
            Mono<MessageChannel> channel = env.getReplyChannel();

            return Mono.just(entityRetriever.getGuildById(member.getGuildId()))
                    .filterWhen(guildConfig -> adminService.isOwner(member).map(bool -> bool && args.length > 0))
                    .flatMap(guildConfig -> Mono.defer(() -> {
                        DateTimeZone timeZone = find(args[0]);
                        if(timeZone == null){
                            String suggest = Strings.findClosest(DateTimeZone.getAvailableIDs(), args[0]);

                            if(suggest != null){
                                return messageService.err(channel, "command.config.unknown-timezone.suggest", suggest);
                            }
                            return messageService.err(channel, "command.config.unknown-timezone");
                        }

                        guildConfig.timeZone(timeZone);
                        entityRetriever.save(guildConfig);
                        return Mono.deferContextual(ctx -> messageService.text(channel, "command.config.timezone-updated", ctx.<Locale>get(KEY_TIMEZONE)))
                                .contextWrite(ctx -> ctx.put(KEY_TIMEZONE, timeZone));
                    }).thenReturn(guildConfig))
                    .switchIfEmpty(args.length == 0 ?
                            messageService.text(channel, "command.config.timezone", env.context().<Locale>get(KEY_TIMEZONE)).then(Mono.empty()) :
                            messageService.err(channel, "command.owner-only").then(Mono.empty()))
                    .then(Mono.empty());
        }

        @Nullable
        private DateTimeZone find(String id){
            try{
                return DateTimeZone.forID(id);
            }catch(Throwable t){
                Exceptions.throwIfJvmFatal(t);
                return null;
            }
        }
    }

    @DiscordCommand(key = "locale", params = "command.config.locale.params", description = "command.config.locale.description")
    public static class LocaleCommand extends Command{
        @Autowired
        private AdminService adminService;

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Member member = env.getAuthorAsMember();
            Mono<MessageChannel> channel = env.getReplyChannel();

            return Mono.just(entityRetriever.getGuildById(member.getGuildId()))
                    .filterWhen(guildConfig -> adminService.isOwner(member).map(bool -> bool && args.length > 0))
                    .flatMap(guildConfig -> Mono.defer(() -> {
                        Locale locale = LocaleUtil.get(args[0]);
                        if(locale == null){
                            String all = LocaleUtil.locales.values().stream()
                                    .map(Locale::toString)
                                    .collect(Collectors.joining(", "));

                            return messageService.text(channel, "command.config.unknown-locale", all);
                        }

                        guildConfig.locale(locale);
                        entityRetriever.save(guildConfig);
                        return Mono.deferContextual(ctx -> messageService.text(channel, "command.config.locale-updated", ctx.<Locale>get(KEY_LOCALE)))
                                .contextWrite(ctx -> ctx.put(KEY_LOCALE, locale));
                    }).thenReturn(guildConfig))
                    .switchIfEmpty(args.length == 0 ?
                            messageService.text(channel, "command.config.locale", env.context().<Locale>get(KEY_LOCALE)).then(Mono.empty()) :
                            messageService.err(channel, "command.owner-only").then(Mono.empty()))
                    .then(Mono.empty());
        }
    }

    @DiscordCommand(key = "mute", params = "command.admin.mute.params", description = "command.admin.mute.description",
                    permissions = {Permission.SEND_MESSAGES, Permission.EMBED_LINKS, Permission.MANAGE_ROLES})
    public static class MuteCommand extends ModeratorCommand{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Mono<MessageChannel> channel = env.getReplyChannel();

            Member author = env.getAuthorAsMember();
            Snowflake targetId = MessageUtil.parseUserId(args[0]);
            Snowflake guildId = author.getGuildId();

            if(entityRetriever.getMuteRoleId(guildId).isEmpty()){
                return messageService.err(channel, "command.disabled.mute");
            }

            DateTime delay = MessageUtil.parseTime(args[1]);
            if(delay == null){
                return messageService.err(channel, "message.error.invalid-time");
            }

            return Mono.justOrEmpty(targetId).flatMap(id -> env.getClient().getMemberById(guildId, id))
                    .switchIfEmpty(messageService.err(channel, "command.incorrect-name").then(Mono.empty()))
                    .filterWhen(member -> BooleanUtils.not(adminService.isMuted(member)))
                    .switchIfEmpty(messageService.err(channel, "command.admin.mute.already-muted").then(Mono.never()))
                    .filterWhen(member -> Mono.zip(adminService.isAdmin(member), adminService.isOwner(author))
                            .map(TupleUtils.function((admin, owner) -> !(admin && !owner))))
                    .switchIfEmpty(messageService.err(channel, "command.admin.user-is-admin").then(Mono.empty()))
                    .flatMap(member -> Mono.defer(() -> {
                        String reason = args.length > 2 ? args[2].trim() : null;

                        if(author.equals(member)){
                            return messageService.err(channel, "command.admin.mute.self-user");
                        }

                        if(reason != null && !reason.isBlank() && reason.length() >= 512){
                            return messageService.err(channel, "common.string-limit", 512);
                        }

                        return adminService.mute(author, member, delay, reason);
                    }))
                    .and(env.getMessage().addReaction(ok));
        }
    }

    @DiscordCommand(key = "delete", params = "command.admin.delete.params", description = "command.admin.delete.description",
                    permissions = {Permission.SEND_MESSAGES, Permission.EMBED_LINKS, Permission.MANAGE_MESSAGES, Permission.READ_MESSAGE_HISTORY})
    public static class DeleteCommand extends ModeratorCommand{
        @Autowired
        private Settings settings;

        @Autowired
        private AuditService auditService;

        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Member author = env.getAuthorAsMember();
            Mono<TextChannel> reply = env.getReplyChannel().cast(TextChannel.class);
            if(!MessageUtil.canParseInt(args[0])){
                return messageService.err(reply, "command.incorrect-number");
            }

            int number = Strings.parseInt(args[0]);
            if(number >= settings.getDiscord().getMaxClearedCount()){
                return messageService.err(reply, "common.limit-number", settings.getDiscord().getMaxClearedCount());
            }

            StringBuffer result = new StringBuffer();
            Instant limit = Instant.now().minus(14, ChronoUnit.DAYS);
            DateTimeFormatter formatter = DateTimeFormat.forPattern("MM-dd-yyyy HH:mm:ss")
                    .withLocale(env.context().get(KEY_LOCALE))
                    .withZone(env.context().get(KEY_TIMEZONE));

            ReusableByteInputStream input = new ReusableByteInputStream();
            BiConsumer<Message, Member> appendInfo = (message, member) -> {
                result.append("[").append(formatter.print(message.getTimestamp().toEpochMilli())).append("] ");
                if(DiscordUtil.isBot(member)){
                    result.append("[BOT] ");
                }

                result.append(member.getUsername());
                member.getNickname().ifPresent(nickname -> result.append(" (").append(nickname).append(")"));
                result.append(" >");
                String content = MessageUtil.effectiveContent(message);
                if(!content.isBlank()){
                    result.append(" ").append(content);
                }
                if(!message.getEmbeds().isEmpty()){
                    result.append(" (... ").append(message.getEmbeds().size()).append(" embed(s))");
                }
                result.append("\n");
            };

            Mono<Void> history = reply.flatMapMany(channel -> channel.getMessagesBefore(env.getMessage().getId())
                    .limitRequest(number)
                    .sort(Comparator.comparing(Message::getId))
                    .filter(message -> message.getTimestamp().isAfter(limit))
                    .flatMap(message -> message.getAuthorAsMember()
                            .doOnNext(member -> {
                                appendInfo.accept(message, member);
                                messageService.deleteById(message.getId());
                            })
                            .thenReturn(message))
                    .transform(messages -> number != 1 ? channel.bulkDeleteMessages(messages).then() : messages.next().flatMap(Message::delete).then()))
                    .then();

            Mono<Void> log =  reply.flatMap(channel -> auditService.log(author.getGuildId(), AuditActionType.MESSAGE_CLEAR)
                    .withUser(author)
                    .withChannel(channel)
                    .withAttribute(COUNT, number)
                    .withAttachment(MESSAGE_TXT, input.writeString(result.toString()))
                    .save());

            return history.then(log).and(env.getMessage().addReaction(ok));
        }
    }

    @DiscordCommand(key = "warn", params = "command.admin.warn.params", description = "command.admin.warn.description",
                    permissions = {Permission.SEND_MESSAGES, Permission.EMBED_LINKS, Permission.BAN_MEMBERS})
    public static class WarnCommand extends ModeratorCommand{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Member author = env.getAuthorAsMember();
            Mono<MessageChannel> channel = env.getReplyChannel();
            Snowflake targetId = MessageUtil.parseUserId(args[0]);
            Snowflake guildId = author.getGuildId();

            return Mono.justOrEmpty(targetId).flatMap(id -> env.getClient().getMemberById(guildId, id))
                    .switchIfEmpty(messageService.err(channel, "command.incorrect-name").then(Mono.never()))
                    .filterWhen(target -> Mono.zip(adminService.isAdmin(target), adminService.isOwner(author))
                            .map(TupleUtils.function((admin, owner) -> !(admin && !owner))))
                    .switchIfEmpty(messageService.err(channel, "command.admin.user-is-admin").then(Mono.empty()))
                    .flatMap(member -> {
                        String reason = args.length > 1 ? args[1].trim() : null;

                        if(Objects.equals(author, member)){
                            return messageService.err(channel, "command.admin.warn.self-user");
                        }

                        if(!Strings.isEmpty(reason) && reason.length() >= 512){
                            return messageService.err(channel, "common.string-limit", 512);
                        }

                        Mono<Void> warnings = Mono.defer(() -> adminService.warnings(member).count()).flatMap(count -> {
                            Mono<Void> message = messageService.text(channel, "command.admin.warn", member.getUsername(), count);

                            AdminConfig config = entityRetriever.getAdminConfigById(guildId);
                            if(count >= config.maxWarnCount()){
                                return message.then(author.getGuild().flatMap(guild ->
                                        guild.ban(member.getId(), spec -> spec.setDeleteMessageDays(0))));
                            }
                            return message;
                        });

                        return adminService.warn(author, member, reason).then(warnings);
                    });
        }
    }

    @DiscordCommand(key = "warnings", params = "command.admin.warnings.params", description = "command.admin.warnings.description")
    public static class WarningsCommand extends ModeratorCommand{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Mono<MessageChannel> channel = env.getReplyChannel();
            Snowflake targetId = MessageUtil.parseUserId(args[0]);
            Snowflake guildId = env.getAuthorAsMember().getGuildId();

            DateTimeFormatter formatter = DateTimeFormat.shortDateTime()
                    .withLocale(env.context().get(KEY_LOCALE))
                    .withZone(env.context().get(KEY_TIMEZONE));

            Collector<Tuple2<Long, AdminAction>, ImmutableEmbedData.Builder, EmbedData> collector = Collector.of(EmbedData::builder,
                    (spec, tuple) -> {
                        long index = tuple.getT1();
                        AdminAction warn = tuple.getT2();
                        String value = String.format("%s%n%s",
                                messageService.format(env.context(), "common.admin", warn.admin().effectiveName()),
                                messageService.format(env.context(), "common.reason", warn.reason()
                                        .orElse(messageService.get(env.context(), "common.not-defined"))));

                        EmbedFieldData field = EmbedFieldData.builder()
                                .name(String.format("%2s. %s", index + 1, formatter.print(warn.timestamp())))
                                .value(value)
                                .inline(true)
                                .build();

                        spec.addField(field);
                    },
                    (builder0, builder1) -> builder0, /* non-mergable */
                    ImmutableEmbedData.Builder::build);

            return Mono.justOrEmpty(targetId).filterWhen(id -> env.getClient().getMemberById(guildId, id).hasElement())
                    .switchIfEmpty(messageService.err(channel, "command.incorrect-name").then(Mono.empty()))
                    .flatMapMany(id -> adminService.warnings(guildId, id))
                    .switchIfEmpty(messageService.text(channel, "command.admin.warnings.empty").then(Mono.never()))
                    .limitRequest(21).index()
                    .collect(collector).flatMap(embed -> messageService.info(channel, spec -> spec.from(embed)));
        }
    }

    @DiscordCommand(key = "unwarn", params = "command.admin.unwarn.params", description = "command.admin.unwarn.description")
    public static class UnwarnCommand extends ModeratorCommand{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Member author = env.getAuthorAsMember();
            Mono<MessageChannel> channel = env.getReplyChannel();
            Snowflake targetId = MessageUtil.parseUserId(args[0]);
            Snowflake guildId = env.getAuthorAsMember().getGuildId();

            if(args.length > 1 && !MessageUtil.canParseInt(args[1])){
                return messageService.err(channel, "command.incorrect-number");
            }

            return Mono.justOrEmpty(targetId).flatMap(id -> env.getClient().getMemberById(guildId, id))
                    .switchIfEmpty(messageService.err(channel, "command.incorrect-name").then(Mono.never()))
                    .filterWhen(target -> adminService.isOwner(author).map(owner -> !target.equals(author) || owner))
                    .switchIfEmpty(messageService.err(channel, "command.admin.unwarn.permission-denied").then(Mono.empty()))
                    .flatMap(target -> adminService.warnings(target).count().flatMap(count -> {
                        int warn = args.length > 1 ? Strings.parseInt(args[1]) : 1;
                        if(count == 0){
                            return messageService.text(channel, "command.admin.warnings.empty");
                        }

                        if(warn > count){
                            return messageService.err(channel, "command.incorrect-number");
                        }

                        return messageService.text(channel, "command.admin.unwarn", target.getUsername(), warn)
                                .and(adminService.unwarn(target, warn - 1));
                    }));
        }
    }

    @DiscordCommand(key = "unmute", params = "command.admin.unmute.params", description = "command.admin.unmute.description",
                    permissions = {Permission.SEND_MESSAGES, Permission.EMBED_LINKS, Permission.MANAGE_ROLES})
    public static class UnmuteCommand extends ModeratorCommand{
        @Override
        public Mono<Void> execute(CommandEnvironment env, String[] args){
            Mono<MessageChannel> channel = env.getReplyChannel();
            Snowflake targetId = MessageUtil.parseUserId(args[0]);
            Snowflake guildId = env.getAuthorAsMember().getGuildId();

            if(entityRetriever.getMuteRoleId(guildId).isEmpty()){
                return messageService.err(channel, messageService.get(env.context(), "command.disabled.mute"));
            }

            return Mono.justOrEmpty(targetId).flatMap(id -> env.getClient().getMemberById(guildId, id))
                    .switchIfEmpty(messageService.err(channel, "command.incorrect-name").then(Mono.empty()))
                    .filterWhen(adminService::isMuted)
                    .flatMap(target -> adminService.unmute(target).thenReturn(target))
                    .switchIfEmpty(messageService.err(channel, "audit.member.unmute.is-not-muted").then(Mono.empty()))
                    .then();
        }
    }
}
