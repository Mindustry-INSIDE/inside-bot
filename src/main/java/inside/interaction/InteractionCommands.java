package inside.interaction;

import com.udojava.evalex.Expression;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.discordjson.json.*;
import discord4j.rest.util.*;
import inside.Settings;
import inside.audit.*;
import inside.command.Commands;
import inside.data.entity.*;
import inside.data.service.AdminService;
import inside.service.MessageService;
import inside.util.*;
import inside.util.func.BooleanFunction;
import inside.util.io.ReusableByteInputStream;
import org.joda.time.*;
import org.joda.time.format.*;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static inside.audit.Attribute.COUNT;
import static inside.audit.BaseAuditProvider.MESSAGE_TXT;
import static inside.util.ContextUtil.*;
import static reactor.bool.BooleanUtils.*;
import static reactor.function.TupleUtils.*;

public class InteractionCommands{

    private InteractionCommands(){}

    public static abstract class GuildCommand extends InteractionCommand{
        @Autowired
        protected Settings settings;

        @Override
        public Mono<Boolean> filter(InteractionCommandEnvironment env){
            if(env.event().getInteraction().getMember().isEmpty()){
                return messageService.err(env.event(), "command.interaction.only-guild").thenReturn(false);
            }
            return Mono.just(true);
        }
    }

    public static abstract class AdminCommand extends GuildCommand{
        @Autowired
        protected AdminService adminService;

        @Override
        public Mono<Boolean> filter(InteractionCommandEnvironment env){
            Mono<Boolean> isAdmin = env.event().getInteraction().getMember()
                    .map(adminService::isAdmin)
                    .orElse(Mono.just(false))
                    .filterWhen(bool -> bool ? Mono.just(true) : messageService.err(env.event(),
                            "common.permission-denied").thenReturn(false));

            return and(super.filter(env), isAdmin);
        }
    }

    public static abstract class OwnerCommand extends GuildCommand{
        @Override
        public Mono<Boolean> filter(InteractionCommandEnvironment env){
            Member member = env.event().getInteraction().getMember().orElse(null);
            if(member == null){
                return Mono.just(false);
            }

            Mono<Boolean> isOwner = member.getGuild().flatMap(Guild::getOwner)
                    .map(member::equals);

            Mono<Boolean> isGuildManager = member.getHighestRole()
                    .map(role -> role.getPermissions().contains(Permission.MANAGE_GUILD));

            Mono<Boolean> resp = or(isOwner, isGuildManager)
                    .filterWhen(bool -> bool ? Mono.just(true) : messageService.err(env.event(),
                            "command.owner-only").thenReturn(false));

            return and(super.filter(env), resp);
        }
    }

    @InteractionDiscordCommand
    public static class SettingsCommand extends OwnerCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            Snowflake guildId = env.event().getInteraction().getGuildId()
                    .orElseThrow(IllegalStateException::new);

            BooleanFunction<String> formatBool = bool ->
                    messageService.get(env.context(), bool ? "command.settings.enabled" : "command.settings.disabled");

            Function<Duration, String> formatDuration = duration -> PeriodFormat.wordBased(env.context().get(KEY_LOCALE))
                    .print(duration.toPeriod());

            Mono<Void> handleStarboard = Mono.justOrEmpty(env.event().getInteraction().getCommandInteraction()
                    .getOption("starboard"))
                    .zipWith(entityRetriever.getStarboardConfigById(guildId)
                            .switchIfEmpty(entityRetriever.createStarboardConfig(guildId)))
                    .flatMap(function((group, starboardConfig) -> {
                        Function<List<EmojiData>, String> formatEmojis = emojis -> {
                            StringBuilder builder = new StringBuilder();
                            int lastnceil = 0;
                            for(EmojiData data : emojis){
                                builder.append(lastnceil).append("..").append(lastnceil + 5);
                                builder.append(" - ");
                                builder.append(DiscordUtil.getEmojiString(data));
                                builder.append("\n");
                                lastnceil += 5;
                            }
                            return builder.toString();
                        };

                        Mono<Void> emojisCommand = Mono.justOrEmpty(group.getOption("emojis"))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("type")
                                .flatMap(ApplicationCommandInteractionOption::getValue))
                                        .map(ApplicationCommandInteractionOptionValue::asString)
                                        .filter(str -> !str.equals("help"))
                                        .switchIfEmpty(messageService.text(env.event(), "command.settings.emojis.current",
                                                formatEmojis.apply(starboardConfig.emojis()))
                                                .then(Mono.empty()))
                                        .zipWith(Mono.justOrEmpty(opt.getOption("value"))
                                                .switchIfEmpty(messageService.text(env.event(), "command.settings.emojis.current",
                                                        formatEmojis.apply(starboardConfig.emojis()))
                                                        .then(Mono.empty()))
                                                .flatMap(subopt -> Mono.justOrEmpty(subopt.getValue()))
                                                .map(ApplicationCommandInteractionOptionValue::asString)))
                                .flatMap(function((choice, enums) -> Mono.defer(() -> {
                                    List<EmojiData> emojis = starboardConfig.emojis();
                                    if(choice.equals("clear")){
                                        emojis.clear();
                                        return messageService.text(env.event(), "command.settings.emojis.clear");
                                    }

                                    boolean add = choice.equals("add");

                                    if(enums.matches("^(#\\d+)$") && !add){ // index mode
                                        String str = enums.substring(1);
                                        if(!MessageUtil.canParseInt(str)){
                                            return messageService.err(env.event(), "command.settings.emojis.overflow-index");
                                        }

                                        int idx = Strings.parseInt(str) - 1; // Counting the index from 1
                                        if(idx < 0 || idx >= emojis.size()){
                                            return messageService.err(env.event(), "command.settings.emojis.index-out-of-bounds");
                                        }

                                        EmojiData value = emojis.remove(idx);
                                        return messageService.text(env.event(), "command.settings.removed",
                                                DiscordUtil.getEmojiString(value));
                                    }

                                    String[] text = enums.split("(\\s+)?,(\\s+)?");
                                    return Flux.fromArray(text).flatMap(str -> env.getClient().getGuildEmojis(guildId)
                                            .filter(emoji -> emoji.asFormat().equals(str) || emoji.getName().equals(str) ||
                                                    emoji.getId().asString().equals(str))
                                            .map(GuildEmoji::getData)
                                            .defaultIfEmpty(EmojiData.builder()
                                                    .name(str)
                                                    .build()))
                                            .collectList()
                                            .flatMap(list -> {
                                                var tmp = new ArrayList<>(emojis);
                                                if(add){
                                                    tmp.addAll(list);
                                                    if(tmp.size() > 20){
                                                        return messageService.err(env.event(), "command.settings.emojis.limit");
                                                    }

                                                    emojis.addAll(list);
                                                }else{
                                                    tmp.removeAll(list);
                                                    if(tmp.size() < 1){
                                                        return messageService.err(env.event(), "command.settings.emojis.no-emojis");
                                                    }

                                                    emojis.removeAll(list);
                                                }

                                                if(add){
                                                    return messageService.text(env.event(), "command.settings.added",
                                                            formatCollection(list, DiscordUtil::getEmojiString));
                                                }
                                                return messageService.text(env.event(), "command.settings.removed",
                                                        formatCollection(list, DiscordUtil::getEmojiString));
                                            });
                                }).and(entityRetriever.save(starboardConfig))));

                        Mono<Void> lowerStarBarrierCommand = Mono.justOrEmpty(group.getOption("lower-star-barrier"))
                                .switchIfEmpty(emojisCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.lower-star-barrier.current",
                                        starboardConfig.lowerStarBarrier()).then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .map(ApplicationCommandInteractionOptionValue::asLong))
                                .filter(l -> l > 0)
                                .switchIfEmpty(messageService.text(env.event(),
                                        "command.settings.negative-number").then(Mono.empty()))
                                .flatMap(l -> {
                                    int i = (int)(long)l;
                                    if(i != l){
                                        return messageService.err(env.event(), "command.settings.overflow-number");
                                    }

                                    starboardConfig.lowerStarBarrier(i);
                                    return messageService.text(env.event(), "command.settings.lower-star-barrier.update", i)
                                            .and(entityRetriever.save(starboardConfig));
                                });

                        Mono<Void> channelCommand = Mono.justOrEmpty(group.getOption("channel"))
                                .switchIfEmpty(lowerStarBarrierCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.starboard-channel.current",
                                        starboardConfig.starboardChannelId().map(DiscordUtil::getChannelMention)
                                                .orElse(messageService.get(env.context(), "command.settings.absent")))
                                        .then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .flatMap(ApplicationCommandInteractionOptionValue::asChannel))
                                .map(Channel::getId)
                                .flatMap(channelId -> {
                                    starboardConfig.starboardChannelId(channelId);
                                    return messageService.text(env.event(), "command.settings.starboard-channel.update",
                                            DiscordUtil.getChannelMention(channelId))
                                            .and(entityRetriever.save(starboardConfig));
                                });

                        return Mono.justOrEmpty(group.getOption("enable"))
                                .switchIfEmpty(channelCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")
                                        .flatMap(ApplicationCommandInteractionOption::getValue)))
                                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.starboard-enable.update",
                                        formatBool.apply(starboardConfig.isEnabled())).then(Mono.empty()))
                                .flatMap(bool -> {
                                    starboardConfig.setEnabled(bool);
                                    return messageService.text(env.event(), "command.settings.starboard-enable.update",
                                            formatBool.apply(bool))
                                            .and(entityRetriever.save(starboardConfig));
                                });
                    }));

            Mono<Void> handleActivities = Mono.justOrEmpty(env.event().getInteraction().getCommandInteraction()
                    .getOption("activities"))
                    .switchIfEmpty(handleStarboard.then(Mono.empty()))
                    .zipWith(entityRetriever.getActiveUserConfigById(guildId)
                            .switchIfEmpty(entityRetriever.createActiveUserConfig(guildId)))
                    .flatMap(function((group, activeUserConfig) -> {
                        Mono<Void> keepCountingPeriodCommand = Mono.justOrEmpty(group.getOption("delay"))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")
                                        .flatMap(ApplicationCommandInteractionOption::getValue)))
                                .map(ApplicationCommandInteractionOptionValue::asString)
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.keep-counting-period.current",
                                        formatDuration.apply(activeUserConfig.keepCountingPeriod())).then(Mono.empty()))
                                .flatMap(str -> {
                                    Duration duration = Optional.ofNullable(MessageUtil.parseDuration(str))
                                            .map(jduration -> Duration.millis(jduration.toMillis()))
                                            .orElse(null);
                                    if(duration == null){
                                        return messageService.err(env.event(), "command.settings.incorrect-duration");
                                    }

                                    activeUserConfig.keepCountingPeriod(duration);
                                    return messageService.text(env.event(), "command.settings.keep-counting-period.update",
                                            formatDuration.apply(duration))
                                            .and(entityRetriever.save(activeUserConfig));
                                });

                        Mono<Void> messageBarrierCommand = Mono.justOrEmpty(group.getOption("message-barrier"))
                                .switchIfEmpty(keepCountingPeriodCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.message-barrier.current",
                                        activeUserConfig.messageBarrier()).then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .map(ApplicationCommandInteractionOptionValue::asLong))
                                .filter(l -> l > 0)
                                .switchIfEmpty(messageService.text(env.event(),
                                        "command.settings.negative-number").then(Mono.empty()))
                                .flatMap(l -> {
                                    int i = (int)(long)l;
                                    if(i != l){
                                        return messageService.err(env.event(), "command.settings.overflow-number");
                                    }

                                    activeUserConfig.messageBarrier(i);
                                    return messageService.text(env.event(), "command.settings.message-barrier.update", i)
                                            .and(entityRetriever.save(activeUserConfig));
                                });

                        Mono<Void> activeUserRoleCommand = Mono.justOrEmpty(group.getOption("active-user-role"))
                                .switchIfEmpty(messageBarrierCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.active-user-role.current",
                                        activeUserConfig.roleId().map(DiscordUtil::getRoleMention)
                                                .orElse(messageService.get(env.context(), "command.settings.absent")))
                                        .then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .flatMap(ApplicationCommandInteractionOptionValue::asRole))
                                .map(Role::getId)
                                .flatMap(roleId -> {
                                    activeUserConfig.roleId(roleId);
                                    return messageService.text(env.event(), "command.settings.active-user-role.update",
                                            DiscordUtil.getRoleMention(roleId))
                                            .and(entityRetriever.save(activeUserConfig));
                                });

                        return Mono.justOrEmpty(group.getOption("enable"))
                                .switchIfEmpty(activeUserRoleCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")
                                        .flatMap(ApplicationCommandInteractionOption::getValue)))
                                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.active-user-enable.update",
                                        formatBool.apply(activeUserConfig.isEnabled())).then(Mono.empty()))
                                .flatMap(bool -> {
                                    activeUserConfig.setEnabled(bool);
                                    return messageService.text(env.event(), "command.settings.active-user-enable.update", formatBool.apply(bool))
                                            .and(entityRetriever.save(activeUserConfig));
                                });
                    }));

            Mono<Void> handleAdmin = Mono.justOrEmpty(env.event().getInteraction().getCommandInteraction()
                    .getOption("admin"))
                    .switchIfEmpty(handleActivities.then(Mono.empty()))
                    .zipWith(entityRetriever.getAdminConfigById(guildId)
                            .switchIfEmpty(entityRetriever.createAdminConfig(guildId)))
                    .flatMap(function((group, adminConfig) -> {
                        Mono<Void> warnThresholdActionCommand = Mono.justOrEmpty(group.getOption("threshold-action")
                                .flatMap(opt -> opt.getOption("value"))
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.threshold-action.current",
                                        String.format("%s (`%s`)", messageService.getEnum(env.context(), adminConfig.thresholdAction()),
                                                adminConfig.thresholdAction())).then(Mono.empty()))
                                .flatMap(str -> {
                                    AdminActionType action = Try.ofCallable(() ->
                                            AdminActionType.valueOf(str)).toOptional().orElse(null);
                                    Objects.requireNonNull(action, "action"); // impossible
                                    adminConfig.thresholdAction(action);

                                    return messageService.text(env.event(), "command.settings.threshold-action.update",
                                            String.format("%s (`%s`)", messageService.getEnum(env.context(), adminConfig.thresholdAction()),
                                                    adminConfig.thresholdAction()))
                                            .and(entityRetriever.save(adminConfig));
                                });

                        Mono<Void> warningsCommand = Mono.justOrEmpty(group.getOption("warnings"))
                                .switchIfEmpty(warnThresholdActionCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.warnings.current",
                                        adminConfig.maxWarnCount()).then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .map(ApplicationCommandInteractionOptionValue::asLong))
                                .flatMap(number -> {
                                    adminConfig.maxWarnCount(number);
                                    return messageService.text(env.event(), "command.settings.warnings.update", number)
                                            .and(entityRetriever.save(adminConfig));
                                });

                        Mono<Void> muteRoleCommand = Mono.justOrEmpty(group.getOption("mute-role"))
                                .switchIfEmpty(warningsCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.mute-role.current",
                                        adminConfig.muteRoleID().map(DiscordUtil::getRoleMention)
                                                .orElse(messageService.get(env.context(), "command.settings.absent")))
                                        .then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .flatMap(ApplicationCommandInteractionOptionValue::asRole))
                                .map(Role::getId)
                                .flatMap(roleId -> {
                                    adminConfig.muteRoleId(roleId);
                                    return messageService.text(env.event(), "command.settings.mute-role.update",
                                            DiscordUtil.getRoleMention(roleId))
                                            .and(entityRetriever.save(adminConfig));
                                });

                        Mono<Void> adminRolesCommand = Mono.justOrEmpty(group.getOption("admin-roles"))
                                .switchIfEmpty(muteRoleCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("type")
                                        .flatMap(ApplicationCommandInteractionOption::getValue))
                                        .map(ApplicationCommandInteractionOptionValue::asString)
                                        .filter(str -> !str.equals("help"))
                                        .switchIfEmpty(messageService.text(env.event(), "command.settings.admin-roles.current",
                                                Optional.of(formatCollection(adminConfig.adminRoleIds(), DiscordUtil::getRoleMention))
                                                        .filter(s -> !s.isBlank())
                                                        .orElse(messageService.get(env.context(), "command.settings.absents")))
                                                .then(Mono.empty()))
                                        .zipWith(Mono.justOrEmpty(opt.getOption("value"))
                                                .flatMap(subopt -> Mono.justOrEmpty(subopt.getValue()))
                                                .map(ApplicationCommandInteractionOptionValue::asString)))
                                .flatMap(function((choice, enums) -> Mono.defer(() -> {
                                    Set<Snowflake> roleIds = adminConfig.adminRoleIds();
                                    if(choice.equals("clear")){
                                        roleIds.clear();
                                        return messageService.text(env.event(), "command.settings.admin-roles.clear");
                                    }

                                    boolean add = choice.equals("add");

                                    List<Snowflake> removed = new ArrayList<>();
                                    String[] text = enums.split("(\\s+)?,(\\s+)?");
                                    Mono<Void> fetch = Flux.fromArray(text)
                                            .flatMap(str -> env.event().getInteraction().getGuild()
                                            .flatMapMany(Guild::getRoles)
                                            .filter(role -> MessageUtil.parseRoleId(str) != null &&
                                                    role.getId().equals(MessageUtil.parseRoleId(str)))
                                            .doOnNext(role -> {
                                                if(add){
                                                    roleIds.add(role.getId());
                                                }else{
                                                    if(roleIds.remove(role.getId())){
                                                        removed.add(role.getId());
                                                    }
                                                }
                                            }))
                                            .then();

                                    return fetch.then(Mono.defer(() -> {
                                        adminConfig.adminRoleIds(roleIds);
                                        if(add){
                                            return messageService.text(env.event(), "command.settings.added",
                                                    formatCollection(roleIds, DiscordUtil::getRoleMention));
                                        }
                                        return messageService.text(env.event(), "command.settings.removed",
                                                formatCollection(removed, DiscordUtil::getRoleMention));
                                    }));
                                }).and(entityRetriever.save(adminConfig))));

                        Mono<Void> warnDelayCommand = Mono.justOrEmpty(group.getOption("warn-delay"))
                                .switchIfEmpty(adminRolesCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")
                                        .flatMap(ApplicationCommandInteractionOption::getValue)))
                                .map(ApplicationCommandInteractionOptionValue::asString)
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.warn-delay.current",
                                        formatDuration.apply(adminConfig.warnExpireDelay())).then(Mono.empty()))
                                .flatMap(str -> {
                                    Duration duration = Optional.ofNullable(MessageUtil.parseDuration(str))
                                            .map(jduration -> Duration.millis(jduration.toMillis()))
                                            .orElse(null);
                                    if(duration == null){
                                        return messageService.err(env.event(), "command.settings.incorrect-duration");
                                    }

                                    adminConfig.warnExpireDelay(duration);
                                    return messageService.text(env.event(), "command.settings.warn-delay.update",
                                            formatDuration.apply(duration))
                                            .and(entityRetriever.save(adminConfig));
                                });

                        return Mono.justOrEmpty(group.getOption("delay"))
                                .switchIfEmpty(warnDelayCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")
                                        .flatMap(ApplicationCommandInteractionOption::getValue)))
                                .map(ApplicationCommandInteractionOptionValue::asString)
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.base-delay.current",
                                        formatDuration.apply(adminConfig.muteBaseDelay())).then(Mono.empty()))
                                .flatMap(str -> {
                                    Duration duration = Optional.ofNullable(MessageUtil.parseDuration(str))
                                            .map(jduration -> Duration.millis(jduration.toMillis()))
                                            .orElse(null);
                                    if(duration == null){
                                        return messageService.err(env.event(), "command.settings.incorrect-duration");
                                    }

                                    adminConfig.muteBaseDelay(duration);
                                    return messageService.text(env.event(), "command.settings.base-delay.update",
                                            formatDuration.apply(duration))
                                            .and(entityRetriever.save(adminConfig));
                                });
                    }));

            Mono<Void> handleAudit = Mono.justOrEmpty(env.event().getInteraction().getCommandInteraction()
                    .getOption("audit"))
                    .switchIfEmpty(handleAdmin.then(Mono.empty()))
                    .zipWith(entityRetriever.getAuditConfigById(guildId)
                            .switchIfEmpty(entityRetriever.createAuditConfig(guildId)))
                    .flatMap(function((group, auditConfig) -> {
                        Mono<Void> channelCommand = Mono.justOrEmpty(group.getOption("channel")
                                .flatMap(command -> command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.log-channel.current",
                                        auditConfig.logChannelId().map(DiscordUtil::getChannelMention)
                                                .orElse(messageService.get(env.context(), "command.settings.absent")))
                                        .then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .flatMap(ApplicationCommandInteractionOptionValue::asChannel))
                                .map(Channel::getId)
                                .flatMap(channelId -> {
                                    auditConfig.logChannelId(channelId);
                                    return messageService.text(env.event(), "command.settings.log-channel.update",
                                            DiscordUtil.getChannelMention(channelId))
                                            .and(entityRetriever.save(auditConfig));
                                });

                        Mono<Void> actionsCommand = Mono.justOrEmpty(group.getOption("actions"))
                                .switchIfEmpty(channelCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("type")
                                        .flatMap(ApplicationCommandInteractionOption::getValue))
                                        .map(ApplicationCommandInteractionOptionValue::asString)
                                        .filter(str -> !str.equals("help"))
                                        .switchIfEmpty(Mono.defer(() -> {
                                            StringBuilder builder = new StringBuilder();
                                            var types = AuditActionType.values();
                                            for(int i = 0; i < types.length; i++){
                                                builder.append(messageService.getEnum(env.context(), types[i]));
                                                if(i + 1 != types.length){
                                                    builder.append(", ");
                                                }
                                                if(i % 3 == 0){
                                                    builder.append("\n");
                                                }
                                            }

                                            return messageService.text(env.event(), "command.settings.actions.all", builder);
                                        }).then(Mono.empty()))
                                        .zipWith(Mono.justOrEmpty(opt.getOption("value"))
                                                .switchIfEmpty(messageService.text(env.event(), "command.settings.actions.current",
                                                        formatCollection(auditConfig.types(), type ->
                                                                messageService.getEnum(env.context(), type)))
                                                        .then(Mono.empty()))
                                                .flatMap(subopt -> Mono.justOrEmpty(subopt.getValue()))
                                                .map(ApplicationCommandInteractionOptionValue::asString)))
                                .flatMap(function((choice, enums) -> Mono.defer(() -> {
                                    Set<AuditActionType> flags = auditConfig.types();
                                    if(choice.equals("clear")){
                                        flags.clear();
                                        return messageService.text(env.event(), "command.settings.actions.clear");
                                    }

                                    List<Tuple2<AuditActionType, String>> all = Arrays.stream(AuditActionType.values())
                                            .map(type -> Tuples.of(type, messageService.getEnum(env.context(), type)))
                                            .collect(Collectors.toUnmodifiableList());

                                    boolean add = choice.equals("add");

                                    Set<String> removed = new HashSet<>();
                                    if(enums.equalsIgnoreCase("all") && add){
                                        flags.addAll(all.stream().map(Tuple2::getT1).collect(Collectors.toSet()));
                                    }else{
                                        String[] text = enums.split("(\\s+)?,(\\s+)?");
                                        for(String s : text){
                                            all.stream().filter(predicate((type, str) -> str.equalsIgnoreCase(s)))
                                                    .findFirst()
                                                    .ifPresent(consumer((type, str) -> {
                                                        if(add){
                                                            flags.add(type);
                                                        }else{
                                                            if(flags.remove(type)){
                                                                removed.add(str);
                                                            }
                                                        }
                                                    }));
                                        }
                                    }

                                    if(add){
                                        String formatted = formatCollection(flags, type ->
                                                messageService.getEnum(env.context(), type));

                                        return messageService.text(env.event(), "command.settings.added", formatted);
                                    }
                                    return messageService.text(env.event(), "command.settings.removed",
                                            String.join(", ", removed));
                                }).and(entityRetriever.save(auditConfig))));

                        return Mono.justOrEmpty(group.getOption("enable"))
                                .switchIfEmpty(actionsCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")
                                        .flatMap(ApplicationCommandInteractionOption::getValue)))
                                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.audit-enable.update",
                                        formatBool.apply(auditConfig.isEnabled())).then(Mono.empty()))
                                .flatMap(bool -> {
                                    auditConfig.setEnabled(bool);
                                    return messageService.text(env.event(), "command.settings.audit-enable.update", formatBool.apply(bool))
                                            .and(entityRetriever.save(auditConfig));
                                });
                    }));

            return Mono.justOrEmpty(env.event().getInteraction().getCommandInteraction()
                    .getOption("common"))
                    .switchIfEmpty(handleAudit.then(Mono.empty()))
                    .zipWith(entityRetriever.getGuildConfigById(guildId)
                            .switchIfEmpty(entityRetriever.createGuildConfig(guildId)))
                    .flatMap(function((group, guildConfig) -> {
                        Mono<Void> reactionRolesCommand = Mono.justOrEmpty(group.getOption("reaction-roles"))
                                .flatMap(opt -> Mono.zip(Mono.justOrEmpty(opt.getOption("type")
                                        .flatMap(ApplicationCommandInteractionOption::getValue))
                                        .map(ApplicationCommandInteractionOptionValue::asString),
                                        Mono.just(opt.getOption("message-id")
                                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                                .map(ApplicationCommandInteractionOptionValue::asString)),
                                        Mono.just(opt.getOption("emoji")
                                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                                .map(ApplicationCommandInteractionOptionValue::asString)),
                                        Mono.just(opt.getOption("role")
                                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                                .map(ApplicationCommandInteractionOptionValue::asSnowflake))))
                                .flatMap(function((choice, messageId, emojistr, role) -> {
                                    if(choice.equals("clear")){
                                        return entityRetriever.deleteAllEmojiDispenserInGuild(guildId)
                                                .then(messageService.text(env.event(), "command.settings.reaction-roles.clear"));
                                    }

                                    Function<EmojiDispenser, String> formatEmojiDispenser = e -> String.format("%s -> %s (%s)\n",
                                            e.messageId().asString(), DiscordUtil.getRoleMention(e.roleId()),
                                            DiscordUtil.getEmojiString(DiscordUtil.toReactionEmoji(e.emoji())));

                                    if(choice.equals("help")){
                                        return entityRetriever.getAllEmojiDispenserInGuild(guildId)
                                                .map(formatEmojiDispenser)
                                                .collect(Collectors.joining())
                                                .flatMap(str -> messageService.text(env.event(),
                                                        "command.settings.reaction-roles.current", str));
                                    }

                                    Mono<Snowflake> preparedRoleId = Mono.justOrEmpty(role)
                                            .switchIfEmpty(messageService.text(env.event(),
                                                    "command.settings.reaction-roles.role-absent").then(Mono.empty()));

                                    Mono<Snowflake> preparedMessageId = Mono.justOrEmpty(messageId)
                                            .switchIfEmpty(messageService.text(env.event(),
                                                    "command.settings.reaction-roles.message-id-absent").then(Mono.empty()))
                                            .mapNotNull(MessageUtil::parseId) // it's safe
                                            .switchIfEmpty(messageService.text(env.event(),
                                                    "command.settings.reaction-roles.incorrect-message-id").then(Mono.empty()));

                                    if(choice.equals("add")){
                                        Mono<EmojiData> fetchEmoji = Mono.justOrEmpty(emojistr)
                                                .switchIfEmpty(messageService.text(env.event(),
                                                        "command.settings.reaction-roles.emoji-absent").then(Mono.empty()))
                                                .flatMap(str -> env.getClient().getGuildEmojis(guildId)
                                                        .filter(emoji -> emoji.asFormat().equals(str) || emoji.getName().equals(str) ||
                                                                emoji.getId().asString().equals(str))
                                                        .map(GuildEmoji::getData)
                                                        .defaultIfEmpty(EmojiData.builder()
                                                                .name(str)
                                                                .build())
                                                        .next());

                                        return Mono.zip(preparedMessageId, fetchEmoji, preparedRoleId)
                                                .filterWhen(ignored -> entityRetriever.getEmojiDispenserCountInGuild(guildId)
                                                        .map(l -> l < 20))
                                                .switchIfEmpty(messageService.text(env.event(),
                                                        "command.settings.reaction-roles.limit").then(Mono.empty()))
                                                .flatMap(function((id, emoji, roleId) -> entityRetriever.createEmojiDispenser(guildId, id, roleId, emoji)
                                                        .flatMap(emojiDispenser -> messageService.text(env.event(), "command.settings.added",
                                                                formatEmojiDispenser.apply(emojiDispenser)))));
                                    }

                                    return Mono.zip(preparedMessageId, preparedRoleId)
                                            .flatMap(function(entityRetriever::getEmojiDispenserById))
                                            .switchIfEmpty(messageService.text(env.event(),
                                                    "command.settings.reaction-roles.not-found").then(Mono.empty()))
                                            .flatMap(emojiDispenser -> entityRetriever.delete(emojiDispenser)
                                                    .thenReturn(emojiDispenser))
                                            .flatMap(emojiDispenser -> messageService.text(env.event(), "command.settings.removed",
                                                    formatEmojiDispenser.apply(emojiDispenser)));
                                }));

                        Mono<Void> timezoneCommand = Mono.justOrEmpty(group.getOption("timezone"))
                                .switchIfEmpty(reactionRolesCommand.then(Mono.empty()))
                                .flatMap(command -> Mono.justOrEmpty(command.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.timezone.current",
                                        guildConfig.timeZone()).then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .map(ApplicationCommandInteractionOptionValue::asString))
                                .flatMap(str -> {
                                    DateTimeZone timeZone = Try.ofCallable(() ->
                                            DateTimeZone.forID(str)).orElse(null);

                                    if(timeZone == null){
                                        String suggest = Strings.findClosest(DateTimeZone.getAvailableIDs(), str);

                                        if(suggest != null){
                                            return messageService.err(env.event(), "command.settings.timezone.unknown.suggest", suggest);
                                        }
                                        return messageService.err(env.event(), "command.settings.timezone.unknown");
                                    }

                                    guildConfig.timeZone(timeZone);
                                    return Mono.deferContextual(ctx -> messageService.text(env.event(),
                                            "command.settings.timezone.update", ctx.<Locale>get(KEY_TIMEZONE)))
                                            .contextWrite(ctx -> ctx.put(KEY_TIMEZONE, timeZone))
                                            .and(entityRetriever.save(guildConfig));
                                });

                        Mono<Void> localeCommand = Mono.justOrEmpty(group.getOption("locale"))
                                .switchIfEmpty(timezoneCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("value")))
                                .switchIfEmpty(messageService.text(env.event(), "command.settings.locale.current",
                                        guildConfig.locale().getDisplayName()).then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getValue())
                                        .map(ApplicationCommandInteractionOptionValue::asString))
                                .flatMap(str -> {
                                    Locale locale = LocaleUtil.get(str);
                                    if(locale == null){
                                        String all = formatCollection(LocaleUtil.locales.values(), locale1 ->
                                                "%s (`%s`)".formatted(locale1.getDisplayName(), locale1.toString()));
                                        return messageService.text(env.event(), "command.settings.locale.all", all);
                                    }

                                    guildConfig.locale(locale);
                                    return Mono.deferContextual(ctx -> messageService.text(env.event(), "command.settings.locale.update",
                                            ctx.<Locale>get(KEY_LOCALE).getDisplayName()))
                                            .contextWrite(ctx -> ctx.put(KEY_LOCALE, locale))
                                            .and(entityRetriever.save(guildConfig));
                                });

                        return Mono.justOrEmpty(group.getOption("prefix"))
                                .switchIfEmpty(localeCommand.then(Mono.empty()))
                                .flatMap(opt -> Mono.justOrEmpty(opt.getOption("type")
                                        .flatMap(ApplicationCommandInteractionOption::getValue))
                                        .map(ApplicationCommandInteractionOptionValue::asString)
                                        .filter(str -> !str.equals("help"))
                                        .switchIfEmpty(messageService.text(env.event(), "command.settings.prefix.current",
                                                String.join(", ", guildConfig.prefixes()))
                                                .then(Mono.empty()))
                                        .zipWith(Mono.justOrEmpty(opt.getOption("value"))
                                                .switchIfEmpty(messageService.text(env.event(), "command.settings.prefix.current",
                                                        String.join(", ", guildConfig.prefixes()))
                                                        .then(Mono.empty()))
                                                .flatMap(subopt -> Mono.justOrEmpty(subopt.getValue()))
                                                .map(ApplicationCommandInteractionOptionValue::asString)))
                                .flatMap(function((choice, enums) -> Mono.defer(() -> {
                                    List<String> flags = guildConfig.prefixes();
                                    if(choice.equals("clear")){
                                        flags.clear();
                                        return messageService.text(env.event(), "command.settings.prefix.clear");
                                    }

                                    boolean add = choice.equals("add");

                                    List<String> removed = new ArrayList<>(0);
                                    String[] text = enums.split("(\\s+)?,(\\s+)?");
                                    for(String s : text){
                                        if(add){
                                            flags.add(s);
                                        }else{
                                            if(flags.remove(s)){
                                                removed.add(s);
                                            }
                                        }
                                    }

                                    if(add){
                                        return messageService.text(env.event(), "command.settings.added",
                                                String.join(", ", flags));
                                    }
                                    return messageService.text(env.event(), "command.settings.removed",
                                            String.join(", ", removed));
                                }).and(entityRetriever.save(guildConfig))));

                    }));
        }

        private static <T> String formatCollection(Collection<? extends T> collection, Function<T, String> formatter){
            return collection.stream()
                    .map(formatter)
                    .collect(Collectors.joining(", "));
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("settings")
                    .description("Configure guild settings.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("common")
                            .description("Different bot settings")
                            .type(ApplicationCommandOptionType.SUB_COMMAND_GROUP.getValue())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("prefix")
                                    .description("Configure bot prefixes")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("type")
                                            .description("Action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .required(true)
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Get a help")
                                                    .value("help")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Add prefix(s). For multiple additions, separate the values with a comma")
                                                    .value("add")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove prefix(s). For multiple deletions, separate the values with a comma")
                                                    .value("remove")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove all prefixes")
                                                    .value("clear")
                                                    .build())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Target prefix(s)")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("locale")
                                    .description("Configure bot locale")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("New locale")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("timezone")
                                    .description("Configure bot time zone")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("New time zone")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("reaction-roles")
                                    .description("Configure reaction roles")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("type")
                                            .description("Action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .required(true)
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Get a help")
                                                    .value("help")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Add reaction role")
                                                    .value("add")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove reaction role")
                                                    .value("remove")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove all reaction roles")
                                                    .value("clear")
                                                    .build())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("message-id")
                                            .description("Target message id")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("emoji")
                                            .description("Target emoji")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("role")
                                            .description("Target role")
                                            .type(ApplicationCommandOptionType.ROLE.getValue())
                                            .build())
                                    .build())
                            .build())
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("activities")
                            .description("Activity features settings")
                            .type(ApplicationCommandOptionType.SUB_COMMAND_GROUP.getValue())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("enable")
                                    .description("Enable activity features")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Boolean value")
                                            .type(ApplicationCommandOptionType.BOOLEAN.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("active-user-role")
                                    .description("Configure active user role")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Active user role")
                                            .type(ApplicationCommandOptionType.ROLE.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("message-barrier")
                                    .description("Configure message barrier")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Minimal message count for reward activity")
                                            .type(ApplicationCommandOptionType.INTEGER.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("keep-counting-period")
                                    .description("Configure keep counting period")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Period value")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .build())
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("starboard")
                            .description("Starboard settings")
                            .type(ApplicationCommandOptionType.SUB_COMMAND_GROUP.getValue())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("enable")
                                    .description("Enable starboard")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Boolean value")
                                            .type(ApplicationCommandOptionType.BOOLEAN.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("lower-star-barrier")
                                    .description("Set star barrier")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Integer value")
                                            .type(ApplicationCommandOptionType.INTEGER.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("emojis")
                                    .description("Configure starboard emojis")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("type")
                                            .description("Action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .required(true)
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Get a help")
                                                    .value("help")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Add emoji(s). For multiple additions, separate the values with a comma")
                                                    .value("add")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove emoji(s). For multiple deletions, separate the values with a comma")
                                                    .value("remove")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove all emojis")
                                                    .value("clear")
                                                    .build())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Target emoji(s)")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("channel")
                                    .description("Configure starboard channel")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Starboard channel")
                                            .type(ApplicationCommandOptionType.CHANNEL.getValue())
                                            .build())
                                    .build())
                            .build())
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("audit")
                            .description("Audit log settings")
                            .type(ApplicationCommandOptionType.SUB_COMMAND_GROUP.getValue())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("enable")
                                    .description("Enable audit logging")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Boolean value")
                                            .type(ApplicationCommandOptionType.BOOLEAN.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("channel")
                                    .description("Configure log channel")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Log channel")
                                            .type(ApplicationCommandOptionType.CHANNEL.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("actions")
                                    .description("Configure bot locale")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("type")
                                            .description("Action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .required(true)
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Get a help")
                                                    .value("help")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Add audit action type(s). For multiple additions, separate the values with a comma")
                                                    .value("add")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove audit action type(s). For multiple deletions, separate the values with a comma")
                                                    .value("remove")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove all audit actions")
                                                    .value("clear")
                                                    .build())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Audit action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .build())
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("admin")
                            .description("Admin settings")
                            .type(ApplicationCommandOptionType.SUB_COMMAND_GROUP.getValue())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("warnings")
                                    .description("Configure max warnings count")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Limit count")
                                            .type(ApplicationCommandOptionType.INTEGER.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("delay")
                                    .description("Configure default delay")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Duration")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("mute-role")
                                    .description("Configure mute role")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Mute role")
                                            .type(ApplicationCommandOptionType.ROLE.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("warn-delay")
                                    .description("Configure warn expire delay")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Duration")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("threshold-action")
                                    .description("Configure warn threshold action")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("ban")
                                                    .value("ban")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("kick")
                                                    .value("kick")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("mute")
                                                    .value("mute")
                                                    .build())
                                            .build())
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("admin-roles")
                                    .description("Configure bot locale")
                                    .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("type")
                                            .description("Action type")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .required(true)
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Get a help")
                                                    .value("help")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Add admin role(s). For multiple additions, separate the values with a comma")
                                                    .value("add")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove admin role(s). For multiple deletions, separate the values with a comma")
                                                    .value("remove")
                                                    .build())
                                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                                    .name("Remove all admin roles")
                                                    .value("clear")
                                                    .build())
                                            .build())
                                    .addOption(ApplicationCommandOptionData.builder()
                                            .name("value")
                                            .description("Admin role")
                                            .type(ApplicationCommandOptionType.STRING.getValue())
                                            .build())
                                    .build())
                            .build())
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class TextLayoutCommand extends InteractionCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            boolean russian = env.event().getInteraction().getCommandInteraction()
                    .getOption("type")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .map("ru"::equals)
                    .orElse(false);

            String text = env.event().getInteraction().getCommandInteraction()
                    .getOption("text")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .map(str -> russian ? Commands.TextLayoutCommand.text2eng(str) : Commands.TextLayoutCommand.text2rus(str))
                    .orElse(MessageService.placeholder);

            return env.event().reply(text);
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("r")
                    .description("Change text layout.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("type")
                            .description("Text layout type")
                            .type(ApplicationCommandOptionType.STRING.getValue())
                            .required(true)
                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                    .name("English layout")
                                    .value("en")
                                    .build())
                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                    .name("Russian layout")
                                    .value("ru")
                                    .build())
                            .build())
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("text")
                            .description("Target text")
                            .type(ApplicationCommandOptionType.STRING.getValue())
                            .required(true)
                            .build())
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class LeetSpeakCommand extends InteractionCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            boolean russian = env.event().getInteraction().getCommandInteraction()
                    .getOption("type")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .map("ru"::equals)
                    .orElse(false);

            String text = env.event().getInteraction().getCommandInteraction()
                    .getOption("text")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .map(str -> Commands.LeetSpeakCommand.leeted(str, russian))
                    .filter(str -> !str.isBlank())
                    .orElse(MessageService.placeholder);

            return env.event().reply(text);
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("1337")
                    .description("Translate text into leet speak.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("type")
                            .description("Leet speak type")
                            .type(ApplicationCommandOptionType.STRING.getValue())
                            .required(true)
                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                    .name("English leet")
                                    .value("en")
                                    .build())
                            .addChoice(ApplicationCommandOptionChoiceData.builder()
                                    .name("Russian leet")
                                    .value("ru")
                                    .build())
                            .build())
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("text")
                            .description("Target text")
                            .type(ApplicationCommandOptionType.STRING.getValue())
                            .required(true)
                            .build())
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class TransliterationCommand extends InteractionCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            String text = env.event().getInteraction().getCommandInteraction()
                    .getOption("text")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .map(Commands.TransliterationCommand::translit)
                    .filter(str -> !str.isBlank())
                    .orElse(MessageService.placeholder);

            return env.event().reply(text);
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("tr")
                    .description("Translating text into transliteration.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("text")
                            .description("Translation text")
                            .type(ApplicationCommandOptionType.STRING.getValue())
                            .required(true)
                            .build())
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class DeleteCommand extends AdminCommand{
        @Autowired
        private AuditService auditService;

        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            Member author = env.event().getInteraction().getMember()
                    .orElseThrow(IllegalStateException::new);

            Mono<TextChannel> reply = env.getReplyChannel().cast(TextChannel.class);

            long number = env.event().getInteraction().getCommandInteraction()
                    .getOption("count")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asLong)
                    .orElse(0L);

            if(number <= 0){
                return messageService.err(env.event(), "command.incorrect-number");
            }

            if(number > settings.getDiscord().getMaxClearedCount()){
                return messageService.err(env.event(), "common.limit-number", settings.getDiscord().getMaxClearedCount());
            }

            StringBuffer result = new StringBuffer();
            Instant limit = Instant.now().minus(14, ChronoUnit.DAYS);
            DateTimeFormatter formatter = DateTimeFormat.forPattern("MM-dd-yyyy HH:mm:ss")
                    .withLocale(env.context().get(KEY_LOCALE))
                    .withZone(env.context().get(KEY_TIMEZONE));

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

            Mono<Void> history = reply.flatMapMany(channel -> channel.getLastMessage()
                    .flatMapMany(message -> channel.getMessagesBefore(message.getId()))
                    .take(number, true)
                    .sort(Comparator.comparing(Message::getId))
                    .filter(message -> message.getTimestamp().isAfter(limit))
                    .flatMap(message -> message.getAuthorAsMember()
                            .doOnNext(member -> appendInfo.accept(message, member))
                            .flatMap(ignored -> entityRetriever.deleteMessageInfoById(message.getId()))
                            .thenReturn(message))
                    .transform(messages -> number > 1 ? channel.bulkDeleteMessages(messages).then() : messages.next().flatMap(Message::delete).then()))
                    .then();

            Mono<Void> log = reply.flatMap(channel -> auditService.log(author.getGuildId(), AuditActionType.MESSAGE_CLEAR)
                    .withUser(author)
                    .withChannel(channel)
                    .withAttribute(COUNT, number)
                    .withAttachment(MESSAGE_TXT, ReusableByteInputStream.ofString(result.toString()))
                    .save());

            return history.then(log).and(env.event().reply("\u2063✅")); // to reduce emoji size
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("delete")
                    .description("Delete some messages.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("count")
                            .description("How many messages to delete")
                            .type(ApplicationCommandOptionType.INTEGER.getValue())
                            .required(true)
                            .build())
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class AvatarCommand extends InteractionCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            return env.event().getInteraction().getCommandInteraction()
                    .getOption("target")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asUser)
                    .orElse(Mono.just(env.event().getInteraction().getUser()))
                    .flatMap(user -> messageService.info(env.event(), embed -> embed.setImage(user.getAvatarUrl() + "?size=512")
                            .setDescription(messageService.format(env.context(), "command.avatar.text", user.getUsername(),
                                    DiscordUtil.getUserMention(user.getId())))));
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("avatar")
                    .description("Get user avatar.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("target")
                            .description("Whose avatar needs to get. By default your avatar")
                            .type(ApplicationCommandOptionType.USER.getValue())
                            .required(false)
                            .build())
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class PingCommand extends InteractionCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            long start = System.currentTimeMillis();
            return env.event().acknowledge().then(env.event().getInteractionResponse()
                    .createFollowupMessage(messageService.get(env.context(), "command.ping.testing"))
                    .map(data -> new Message(env.getClient(), data))
                    .flatMap(message -> env.event().getInteractionResponse()
                            .editFollowupMessage(message.getId().asLong(), WebhookMessageEditRequest.builder()
                                    .content(messageService.format(env.context(), "command.ping.completed",
                                            System.currentTimeMillis() - start))
                                    .build(), true))
                    .then());
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("ping")
                    .description("Get bot ping.")
                    .build();
        }
    }

    @InteractionDiscordCommand
    public static class MathCommand extends InteractionCommand{
        @Override
        public Mono<Void> execute(InteractionCommandEnvironment env){
            String expression = env.event().getInteraction().getCommandInteraction()
                    .getOption("expression")
                    .flatMap(ApplicationCommandInteractionOption::getValue)
                    .map(ApplicationCommandInteractionOptionValue::asString)
                    .orElseThrow(IllegalStateException::new);

            return Commands.MathCommand.createExpression(expression).publishOn(Schedulers.boundedElastic())
                    .onErrorResume(t -> t instanceof ArithmeticException || t instanceof Expression.ExpressionException ||
                            t instanceof NumberFormatException,
                    t -> messageService.error(env.event(), "command.math.error.title", t.getMessage()).then(Mono.empty()))
                    .flatMap(decimal -> messageService.text(env.event(), MessageUtil.substringTo(decimal.toString(), Message.MAX_CONTENT_LENGTH)));
        }

        @Override
        public ApplicationCommandRequest getRequest(){
            return ApplicationCommandRequest.builder()
                    .name("math")
                    .description("Calculate math expression.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("expression")
                            .description("Math expression")
                            .type(ApplicationCommandOptionType.STRING.getValue())
                            .required(true)
                            .build())
                    .build();
        }
    }
}
