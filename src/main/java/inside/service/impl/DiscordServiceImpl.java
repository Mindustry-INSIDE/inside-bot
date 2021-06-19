package inside.service.impl;

import discord4j.common.store.Store;
import discord4j.common.store.legacy.LegacyStoreLayout;
import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.discordjson.json.PresenceData;
import discord4j.gateway.intent.*;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import discord4j.store.api.mapping.MappingStoreService;
import discord4j.store.api.noop.NoOpStoreService;
import discord4j.store.jdk.JdkStoreService;
import inside.Settings;
import inside.data.entity.Activity;
import inside.data.service.EntityRetriever;
import inside.interaction.*;
import inside.service.DiscordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

import static reactor.function.TupleUtils.*;

@Service
public class DiscordServiceImpl implements DiscordService{

    private GatewayDiscordClient gateway;

    @Autowired(required = false)
    private List<InteractionCommand> commands;

    @Autowired(required = false)
    private ReactiveEventAdapter[] adapters;

    @Autowired
    private Settings settings;

    @Autowired
    private EntityRetriever entityRetriever;

    @PostConstruct
    public void init(){
        String token = settings.getToken();
        Objects.requireNonNull(token, "token");

        gateway = DiscordClientBuilder.create(token)
                .onClientResponse(ResponseFunction.emptyIfNotFound())
                .onClientResponse(ResponseFunction.emptyOnErrorStatus(RouteMatcher.route(Routes.REACTION_CREATE), 400))
                .build()
                .gateway()
                .setMemberRequestFilter(MemberRequestFilter.all())
                .setStore(Store.fromLayout(LegacyStoreLayout.of(MappingStoreService.create()
                        .setMapping(new NoOpStoreService(), PresenceData.class)
                        // .setMapping(new CaffeineStoreService(caffeine -> caffeine.weakKeys()
                        //         .expireAfterWrite(Duration.ofDays(3))), MessageData.class)
                        .setFallback(new JdkStoreService()))))
                .setEnabledIntents(IntentSet.of(
                        Intent.GUILDS,
                        Intent.GUILD_MEMBERS,
                        Intent.GUILD_MESSAGES,
                        Intent.GUILD_VOICE_STATES,
                        Intent.GUILD_MESSAGE_REACTIONS,
                        Intent.DIRECT_MESSAGES
                ))
                .login()
                .block();

        Objects.requireNonNull(gateway, "impossible"); // for ide

        long applicationId = Objects.requireNonNull(gateway.rest().getApplicationId().block(), "impossible");
        gateway.rest().getApplicationService()
                .bulkOverwriteGlobalApplicationCommand(applicationId, commands.stream()
                        .map(InteractionCommand::getRequest)
                        .collect(Collectors.toList()))
                .subscribe();

        gateway.on(ReactiveEventAdapter.from(adapters)).subscribe();
    }

    @Override
    public Mono<Void> handle(InteractionCommandEnvironment env){
        return Mono.justOrEmpty(commands.stream()
                .filter(cmd -> cmd.getRequest().name().equals(env.event().getCommandName()))
                .findFirst())
                .filterWhen(cmd -> cmd.filter(env))
                .flatMap(cmd -> cmd.execute(env));
    }

    @PreDestroy
    public void destroy(){
        gateway.logout().block();
    }

    @Override
    public List<InteractionCommand> getCommands(){
        return commands;
    }

    @Override // for monitors
    public GatewayDiscordClient gateway(){
        return gateway;
    }

    @Override
    public Mono<TextChannel> getTextChannelById(Snowflake channelId){
        return gateway.getChannelById(channelId).ofType(TextChannel.class);
    }

    @Scheduled(cron = "0 */2 * * * *")
    private void activeUsers(){
        entityRetriever.getAllLocalMembers()
                .flatMap(localMember -> Mono.zip(Mono.just(localMember),
                        gateway.getMemberById(localMember.guildId(), localMember.userId()),
                        entityRetriever.getActiveUserConfigById(localMember.guildId())))
                .filter(predicate((localMember, member, activeUserConfig) -> activeUserConfig.isEnabled()))
                .flatMap(function((localMember, member, activeUserConfig) -> Mono.defer(() -> {
                    Snowflake roleId = activeUserConfig.roleId().orElse(null);
                    if(roleId == null){
                        return Mono.empty();
                    }

                    Activity activity = localMember.activity();
                    if(activeUserConfig.isActive(activity)){
                        return member.addRole(roleId);
                    }

                    return member.removeRole(roleId);
                }).and(Mono.defer(() -> {
                    if(activeUserConfig.resetIfAfter(localMember.activity())){
                        return entityRetriever.save(localMember);
                    }
                    return Mono.empty();
                }))))
                .subscribe();
    }
}
