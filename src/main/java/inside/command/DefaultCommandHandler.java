package inside.command;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import inside.command.model.*;
import inside.data.entity.GuildConfig;
import inside.data.service.EntityRetriever;
import inside.service.MessageService;
import inside.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

@Service
public class DefaultCommandHandler implements CommandHandler{

    private final EntityRetriever entityRetriever;

    private final MessageService messageService;

    private final CommandHolder commandHolder;

    public DefaultCommandHandler(@Autowired EntityRetriever entityRetriever,
                                 @Autowired MessageService messageService,
                                 @Autowired CommandHolder commandHolder){
        this.entityRetriever = entityRetriever;
        this.messageService = messageService;
        this.commandHolder = commandHolder;
    }

    @Override
    public Mono<Void> handleMessage(CommandEnvironment environment){
        String message = environment.getMessage().getContent();
        Snowflake guildId = environment.getAuthorAsMember().getGuildId();
        Snowflake selfId = environment.getClient().getSelfId();
        Mono<Guild> guild = environment.getMessage().getGuild();

        Mono<String> prefix = entityRetriever.getGuildConfigById(guildId)
                .switchIfEmpty(entityRetriever.createGuildConfig(guildId))
                .flatMap(guildConfig -> Mono.justOrEmpty(guildConfig.prefixes().stream()
                        .filter(message::startsWith)
                        .findFirst()));

        Mono<String> mention = Mono.just(message)
                .filter(s -> environment.getMessage().getUserMentionIds().contains(selfId))
                .filter(s -> s.startsWith(DiscordUtil.getMemberMention(selfId)) ||
                        s.startsWith(DiscordUtil.getUserMention(selfId)))
                .map(s -> s.startsWith(DiscordUtil.getMemberMention(selfId)) ? DiscordUtil.getMemberMention(selfId) :
                        DiscordUtil.getUserMention(selfId));

        Mono<Tuple2<String, String>> text = prefix.switchIfEmpty(mention)
                .map(s -> message.substring(s.length()).trim())
                .zipWhen(s -> Mono.just(s.contains(" ") ? s.substring(0, s.indexOf(" ")) : s).map(String::toLowerCase))
                .cache();

        Mono<Void> suggestion = text.map(Tuple2::getT1).flatMap(commandName -> commandHolder.getCommandInfoMap().values().stream()
                .flatMap(commandInfo -> Arrays.stream(commandInfo.text()))
                .min(Comparator.comparingInt(s -> Strings.levenshtein(s, commandName)))
                .map(s -> messageService.err(environment, "command.response.found-closest", s))
                .orElse(prefix.map(GuildConfig::formatPrefix).flatMap(str ->
                        messageService.err(environment, "command.response.unknown", str)))
                .doFirst(() -> messageService.awaitEdit(environment.getMessage().getId())));

        return text.flatMap(TupleUtils.function((commandstr, cmdkey) -> Mono.justOrEmpty(commandHolder.getCommand(cmdkey))
                .switchIfEmpty(suggestion.then(Mono.empty()))
                .flatMap(command -> {
                    CommandInfo info = commandHolder.getCommandInfoMap().get(command);
                    List<CommandOption> result = new ArrayList<>();
                    String argstr = commandstr.contains(" ") ? commandstr.substring(cmdkey.length() + 1) : "";
                    int index = 0;
                    boolean satisfied = false;
                    String argsres = info.paramText().isEmpty() ? "command.response.incorrect-arguments.empty" :
                            "command.response.incorrect-arguments";

                    if(argstr.matches("^(?i)(help|\\?)$")){
                        return command.filter(environment).flatMap(bool -> bool ? command.help(environment) : Mono.empty());
                    }

                    while(true){
                        if(index >= info.params().length && !argstr.isEmpty()){
                            messageService.awaitEdit(environment.getMessage().getId());
                            return prefix.map(GuildConfig::formatPrefix)
                                    .flatMap(str -> messageService.error(environment, "command.response.many-arguments.title",
                                            argsres, str, cmdkey, messageService.get(environment.context(), info.paramText())));
                        }else if(argstr.isEmpty()){
                            break;
                        }

                        if(info.params()[index].optional() || index >= info.params().length - 1 || info.params()[index + 1].optional()){
                            satisfied = true;
                        }

                        if(info.params()[index].variadic()){
                            result.add(new CommandOption(info.params()[index], argstr));
                            break;
                        }

                        int next = findSpace(argstr);
                        if(next == -1){
                            if(!satisfied){
                                messageService.awaitEdit(environment.getMessage().getId());
                                return prefix.map(GuildConfig::formatPrefix)
                                        .flatMap(str -> messageService.error(environment, "command.response.few-arguments.title",
                                                argsres, str, cmdkey, messageService.get(environment.context(), info.paramText())));
                            }
                            result.add(new CommandOption(info.params()[index], argstr));
                            break;
                        }else{
                            String arg = argstr.substring(0, next);
                            argstr = argstr.substring(arg.length() + 1);
                            if(arg.isBlank()) continue;
                            result.add(new CommandOption(info.params()[index], arg));
                        }

                        index++;
                    }

                    if(!satisfied && info.params().length > 0 && !info.params()[0].optional() &&
                            environment.getMessage().getMessageReference().isEmpty()){
                        messageService.awaitEdit(environment.getMessage().getId());
                        return prefix.map(GuildConfig::formatPrefix)
                                .flatMap(str -> messageService.error(environment, "command.response.few-arguments.title",
                                        argsres, str, cmdkey, messageService.get(environment.context(), info.paramText())));
                    }

                    Predicate<Throwable> missingAccess = t -> t.getMessage() != null &&
                            (t.getMessage().contains("Missing Access") ||
                            t.getMessage().contains("Missing Permissions"));

                    Function<Throwable, Mono<Void>> fallback = t -> Flux.fromIterable(info.permissions())
                            .filterWhen(permission -> environment.getReplyChannel().cast(GuildMessageChannel.class)
                                    .flatMap(targetChannel ->
                                    targetChannel.getEffectivePermissions(selfId))
                                    .map(set -> !set.contains(permission)))
                            .map(permission -> messageService.getEnum(environment.context(), permission))
                            .map("• "::concat)
                            .collect(Collectors.joining("\n"))
                            .filter(s -> !s.isBlank())
                            .flatMap(s -> messageService.text(environment, String.format("%s%n%n%s",
                            messageService.get(environment.context(), "message.error.permission-denied.title"),
                            messageService.format(environment.context(), "message.error.permission-denied.description", s)))
                            .onErrorResume(missingAccess, t0 -> guild.flatMap(Guild::getOwner)
                                    .flatMap(User::getPrivateChannel)
                                    .transform(c -> messageService.info(c,
                                            messageService.get(environment.context(), "message.error.permission-denied.title"),
                                            messageService.format(environment.context(), "message.error.permission-denied.description", s)))));

                    return Mono.just(command)
                            .filterWhen(c -> c.filter(environment))
                            .flatMap(c -> c.execute(environment, new CommandInteraction(cmdkey, result)))
                            .doFirst(() -> messageService.removeEdit(environment.getMessage().getId()))
                            .onErrorResume(missingAccess, fallback);
                })));
    }

    private int findSpace(String text){
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(Character.isWhitespace(c) && (i + 1 < text.length() && !Character.isWhitespace(text.charAt(i + 1)) ||
                    i - 1 != -1 && !Character.isWhitespace(text.charAt(i -1)))){
                return i;
            }
        }
        return -1;
    }
}
