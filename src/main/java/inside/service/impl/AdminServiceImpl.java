package inside.service.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.*;
import discord4j.rest.util.Permission;
import inside.audit.*;
import inside.data.entity.*;
import inside.data.repository.AdminActionRepository;
import inside.data.service.EntityRetriever;
import inside.scheduler.job.*;
import inside.service.AdminService;
import inside.util.Try;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.*;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.*;

import static inside.audit.Attribute.*;
import static reactor.function.TupleUtils.function;

@Service
public class AdminServiceImpl implements AdminService{

    private final AdminActionRepository repository;

    private final EntityRetriever entityRetriever;

    private final AuditService auditService;

    private final SchedulerFactoryBean schedulerFactoryBean;

    public AdminServiceImpl(@Autowired AdminActionRepository repository,
                            @Autowired EntityRetriever entityRetriever,
                            @Autowired AuditService auditService,
                            @Autowired SchedulerFactoryBean schedulerFactoryBean){
        this.repository = repository;
        this.entityRetriever = entityRetriever;
        this.auditService = auditService;
        this.schedulerFactoryBean = schedulerFactoryBean;
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<AdminAction> get(AdminActionType type, Snowflake guildId, Snowflake targetId){
        return Flux.defer(() -> Flux.fromIterable(repository.find(type, guildId.asLong(), targetId.asLong())));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<AdminAction> getAll(AdminActionType type){
        return Flux.defer(() -> Flux.fromIterable(repository.findAll(type)));
    }

    @Override
    @Transactional
    public Mono<Void> mute(Member admin, Member target, Instant endTimestamp, @Nullable String reason){
        Mono<Void> saveAction = entityRetriever.getAndUpdateLocalMemberById(admin)
                .zipWith(entityRetriever.getAndUpdateLocalMemberById(target))
                .flatMap(function((adminLocalMember, targetLocalMember) -> Mono.fromRunnable(() -> repository.save(AdminAction.builder()
                        .guildId(admin.getGuildId())
                        .type(AdminActionType.mute)
                        .admin(adminLocalMember)
                        .target(targetLocalMember)
                        .reason(reason)
                        .timestamp(Instant.now())
                        .endTimestamp(endTimestamp)
                        .build()))));

        Mono<Void> log = auditService.newBuilder(admin.getGuildId(), AuditActionType.MEMBER_MUTE)
                .withUser(admin)
                .withTargetUser(target)
                .withAttribute(REASON, reason)
                .withAttribute(DELAY, endTimestamp)
                .save();

        Mono<Void> addRole = entityRetriever.getAdminConfigById(admin.getGuildId())
                .flatMap(adminConfig -> Mono.justOrEmpty(adminConfig.muteRoleID()))
                .flatMap(target::addRole);

        Mono<Void> scheduleUnmute = Mono.deferContextual(ctx -> Mono.fromRunnable(() -> Try.run(() ->
                schedulerFactoryBean.getScheduler().scheduleJob(UnmuteJob.createDetails(target), TriggerBuilder.newTrigger()
                        .startAt(Date.from(endTimestamp))
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                        .build()))));

        return Mono.when(saveAction, addRole, log, scheduleUnmute);
    }

    @Override
    public Mono<Boolean> isMuted(Snowflake guildId, Snowflake targetId){
        return get(AdminActionType.mute, guildId, targetId).hasElements();
    }

    @Override
    @Transactional
    public Mono<Void> unmute(Member target){
        Mono<Void> createIfAbsent = entityRetriever.getAndUpdateLocalMemberById(target)
                .switchIfEmpty(entityRetriever.createLocalMember(target))
                .then();

        Mono<Void> remove = get(AdminActionType.mute, target.getGuildId(), target.getId()).next()
                .flatMap(adminAction -> Mono.fromRunnable(() -> repository.delete(adminAction)))
                .then();

        Mono<Void> log = auditService.newBuilder(target.getGuildId(), AuditActionType.MEMBER_UNMUTE)
                .withTargetUser(target)
                .save();

        Mono<Void> removeRole = entityRetriever.getAdminConfigById(target.getGuildId())
                .switchIfEmpty(entityRetriever.createAdminConfig(target.getGuildId()))
                .flatMap(adminConfig -> Mono.justOrEmpty(adminConfig.muteRoleID()))
                .flatMap(target::removeRole);

        return Mono.when(createIfAbsent, removeRole, log, remove);
    }

    @Override
    @Transactional
    public Mono<Void> warn(Member admin, Member target, @Nullable String reason){
        Mono<AdminConfig> getOrCreateAdminConfig = entityRetriever.getAdminConfigById(admin.getGuildId())
                .switchIfEmpty(entityRetriever.createAdminConfig(admin.getGuildId()));

        return Mono.zip(entityRetriever.getAndUpdateLocalMemberById(admin), entityRetriever.getAndUpdateLocalMemberById(target),
                getOrCreateAdminConfig)
                .map(function((adminLocalMember, targetLocalMember, adminConfig) -> repository.save(AdminAction.builder()
                        .guildId(admin.getGuildId())
                        .type(AdminActionType.warn)
                        .admin(adminLocalMember)
                        .target(targetLocalMember)
                        .reason(reason)
                        .timestamp(Instant.now())
                        .endTimestamp(Optional.ofNullable(adminConfig.warnExpireDelay())
                                .map(jduration -> Instant.now().plus(jduration))
                                .orElse(null))
                        .build())))
                .filter(action -> action.endTimestamp().isPresent())
                .map(action -> Try.run(() -> schedulerFactoryBean.getScheduler().scheduleJob(UnwarnJob.createDetails(action), TriggerBuilder.newTrigger()
                        .startAt(action.endTimestamp()
                                .map(Date::from)
                                .orElseThrow(IllegalStateException::new))
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                        .build())))
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> unwarn(Snowflake guildId, Snowflake targetId, int index){
        return warnings(guildId, targetId).elementAt(index)
                .flatMap(action -> Mono.fromRunnable(() -> repository.delete(action)));
    }

    @Override
    public Flux<AdminAction> warnings(Snowflake guildId, Snowflake targetId){
        return get(AdminActionType.warn, guildId, targetId);
    }

    @Override
    public Mono<Boolean> isOwner(Member member){
        Objects.requireNonNull(member, "member");
        return member.getGuild().map(Guild::getOwnerId).map(ownerId -> member.getId().equals(ownerId));
    }

    @Override
    public Mono<Boolean> isAdmin(Member member){
        Mono<Set<Snowflake>> roles = entityRetriever.getAdminConfigById(member.getGuildId())
                .map(AdminConfig::adminRoleIds);

        Mono<Boolean> isPermissed = member.getRoles().map(Role::getId)
                .filterWhen(id -> roles.map(list -> list.contains(id)))
                .hasElements();

        Mono<Boolean> isAdmin = member.getRoles().map(Role::getPermissions)
                .any(set -> set.contains(Permission.ADMINISTRATOR));

        return Mono.zip(isOwner(member), isAdmin, isPermissed).map(function((owner, admin, permissed) -> owner || admin || permissed));
    }
}
