package inside.data.service.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.*;
import discord4j.discordjson.json.EmojiData;
import discord4j.store.api.util.LongLongTuple2;
import inside.Settings;
import inside.data.entity.*;
import inside.data.service.*;
import inside.service.MessageService;
import inside.util.MessageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;

import java.util.*;

@Service
public class EntityRetrieverImpl implements EntityRetriever{

    // NOTE: if you do not set the id, then you get an analog of ReactionEmoji.Unicode
    private static final List<EmojiData> defaultStarsEmojis = Arrays.asList(
            EmojiData.builder().name("\u2B50").build(),
            EmojiData.builder().name("\uD83C\uDF1F").build(),
            EmojiData.builder().name("\uD83D\uDCAB").build()
    );

    private final StoreHolder storeHolder;

    private final Settings settings;

    private final MessageService messageService;

    public EntityRetrieverImpl(@Autowired StoreHolder storeHolder,
                               @Autowired Settings settings,
                               @Autowired MessageService messageService){
        this.storeHolder = storeHolder;
        this.settings = settings;
        this.messageService = messageService;
    }

    @Override
    public Mono<GuildConfig> getGuildConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getGuildConfigService().find(guildId.asLong());
    }

    @Override
    public Mono<Void> save(GuildConfig guildConfig){
        Objects.requireNonNull(guildConfig, "guildConfig");
        return storeHolder.getGuildConfigService().save(guildConfig);
    }

    @Override
    public Mono<Void> deleteGuildConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getGuildConfigService().delete(guildId.asLong());
    }

    @Override
    public Mono<AdminConfig> getAdminConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getAdminConfigService().find(guildId.asLong());
    }

    @Override
    public Mono<Void> save(AdminConfig adminConfig){
        Objects.requireNonNull(adminConfig, "adminConfig");
        return storeHolder.getAdminConfigService().save(adminConfig);
    }

    @Override
    public Mono<Void> deleteAdminConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getActivityConfigService().delete(guildId.asLong());
    }

    @Override
    public Mono<AuditConfig> getAuditConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getAuditConfigService().find(guildId.asLong());
    }

    @Override
    public Mono<Void> save(AuditConfig auditConfig){
        Objects.requireNonNull(auditConfig, "auditConfig");
        return storeHolder.getAuditConfigService().save(auditConfig);
    }

    @Override
    public Mono<Void> deleteAuditConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getAuditConfigService().delete(guildId.asLong());
    }

    @Override
    public Flux<LocalMember> getAllLocalMembers(){
        return storeHolder.getLocalMemberService().getAll();
    }

    @Override
    public Mono<LocalMember> getLocalMemberById(Snowflake userId, Snowflake guildId){
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getLocalMemberService().find(LongLongTuple2.of(userId.asLong(), guildId.asLong()));
    }

    @Override
    public Mono<LocalMember> getAndUpdateLocalMemberById(Member member){
        Objects.requireNonNull(member, "member");
        return getLocalMemberById(member.getId(), member.getGuildId())
                .flatMap(localMember -> Mono.defer(() -> {
                    if(!localMember.effectiveName().equals(member.getDisplayName())){
                        localMember.effectiveName(member.getDisplayName());
                        return save(localMember);
                    }
                    return Mono.empty();
                }).thenReturn(localMember));
    }

    @Override
    public Mono<Void> save(LocalMember localMember){
        Objects.requireNonNull(localMember, "localMember");
        return storeHolder.getLocalMemberService().save(localMember);
    }

    @Override
    public Mono<Void> deleteAllLocalMembersInGuild(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getLocalMemberService().deleteAllByGuildId(guildId.asLong());
    }

    @Override
    public Mono<MessageInfo> getMessageInfoById(Snowflake messageId){
        Objects.requireNonNull(messageId, "messageId");
        return storeHolder.getMessageInfoService().find(messageId.asLong());
    }

    @Override
    public Mono<Void> save(MessageInfo messageInfo){
        Objects.requireNonNull(messageInfo, "messageInfo");
        return storeHolder.getMessageInfoService().save(messageInfo);
    }

    @Override
    public Mono<Void> deleteMessageInfoById(Snowflake messageId){
        return getMessageInfoById(messageId).flatMap(this::delete);
    }

    @Override
    public Mono<Void> deleteAllMessageInfoInGuild(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getMessageInfoService().deleteAllByGuildId(guildId.asLong());
    }

    @Override
    public Mono<Void> delete(MessageInfo messageInfo){
        Objects.requireNonNull(messageInfo, "messageInfo");
        return storeHolder.getMessageInfoService().delete(messageInfo);
    }

    @Override
    public Mono<StarboardConfig> getStarboardConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getStarboardConfigService().find(guildId.asLong());
    }

    @Override
    public Mono<Void> save(StarboardConfig starboardConfig){
        Objects.requireNonNull(starboardConfig, "starboardConfig");
        return storeHolder.getStarboardConfigService().save(starboardConfig);
    }

    @Override
    public Mono<Void> deleteStarboardConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getStarboardConfigService().delete(guildId.asLong());
    }

    @Override
    public Mono<Starboard> getStarboardById(Snowflake guildId, Snowflake sourceMessageId){
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(sourceMessageId, "sourceMessageId");
        return storeHolder.getStarboardService().find(LongLongTuple2.of(guildId.asLong(), sourceMessageId.asLong()));
    }

    @Override
    public Mono<Void> save(Starboard starboard){
        Objects.requireNonNull(starboard, "starboard");
        return storeHolder.getStarboardService().save(starboard);
    }

    @Override
    public Mono<Void> deleteStarboardById(Snowflake guildId, Snowflake sourceMessageId){
        return getStarboardById(guildId, sourceMessageId).flatMap(this::delete);
    }

    @Override
    public Mono<Void> delete(Starboard starboard){
        Objects.requireNonNull(starboard, "starboard");
        return storeHolder.getStarboardService().delete(starboard);
    }

    @Override
    public Mono<Void> deleteAllStarboardsInGuild(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getStarboardService().deleteAllByGuildId(guildId.asLong());
    }

    @Override
    public Mono<ActivityConfig> getActivityConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getActivityConfigService().find(guildId.asLong());
    }

    @Override
    public Mono<Void> save(ActivityConfig activityConfig){
        Objects.requireNonNull(activityConfig, "activeUserConfig");
        return storeHolder.getActivityConfigService().save(activityConfig);
    }

    @Override
    public Mono<Void> deleteActivityConfigById(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getActivityConfigService().delete(guildId.asLong());
    }

    @Override
    public Flux<EmojiDispenser> getEmojiDispensersById(Snowflake messageId){
        Objects.requireNonNull(messageId, "messageId");
        return storeHolder.getEmojiDispenserService().getAllByMessageId(messageId.asLong());
    }

    @Override
    public Mono<EmojiDispenser> getEmojiDispenserById(Snowflake messageId, Snowflake roleId){
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(roleId, "roleId");
        return storeHolder.getEmojiDispenserService().find(LongLongTuple2.of(messageId.asLong(), roleId.asLong()));
    }

    @Override
    public Flux<EmojiDispenser> getAllEmojiDispenserInGuild(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getEmojiDispenserService().getAllByGuildId(guildId.asLong());
    }

    @Override
    public Mono<Long> getEmojiDispenserCountInGuild(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getEmojiDispenserService().countAllByGuildId(guildId.asLong());
    }

    @Override
    public Mono<Void> save(EmojiDispenser emojiDispenser){
        Objects.requireNonNull(emojiDispenser, "emojiDispenser");
        return storeHolder.getEmojiDispenserService().save(emojiDispenser);
    }

    @Override
    public Mono<Void> deleteEmojiDispenserById(Snowflake messageId, Snowflake roleId){
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(roleId, "roleId");
        return storeHolder.getEmojiDispenserService().delete(LongLongTuple2.of(messageId.asLong(), roleId.asLong()));
    }

    @Override
    public Mono<Void> delete(EmojiDispenser emojiDispenser){
        Objects.requireNonNull(emojiDispenser, "emojiDispenser");
        return storeHolder.getEmojiDispenserService().delete(emojiDispenser);
    }

    @Override
    public Mono<Void> deleteAllEmojiDispenserInGuild(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return storeHolder.getEmojiDispenserService().deleteAllByGuildId(guildId.asLong());
    }

    @Override
    public Mono<GuildConfig> createGuildConfig(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return Mono.defer(() -> {
            GuildConfig guildConfig = new GuildConfig();
            guildConfig.guildId(guildId);
            guildConfig.prefixes(settings.getDefaults().getPrefixes());
            guildConfig.locale(messageService.getDefaultLocale());
            guildConfig.timeZone(settings.getDefaults().getTimeZone());
            return save(guildConfig).thenReturn(guildConfig);
        });
    }

    @Override
    public Mono<AdminConfig> createAdminConfig(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return Mono.defer(() -> {
            AdminConfig adminConfig = new AdminConfig();
            adminConfig.guildId(guildId);
            adminConfig.maxWarnCount(settings.getDefaults().getMaxWarnings());
            adminConfig.muteBaseDelay(settings.getDefaults().getMuteEvade());
            adminConfig.warnExpireDelay(settings.getDefaults().getWarnExpire());
            adminConfig.thresholdAction(settings.getDefaults().getThresholdAction());
            return save(adminConfig).thenReturn(adminConfig);
        });
    }

    @Override
    public Mono<AuditConfig> createAuditConfig(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return Mono.defer(() -> {
            AuditConfig auditConfig = new AuditConfig();
            auditConfig.guildId(guildId);
            return save(auditConfig).thenReturn(auditConfig);
        });
    }

    @Override
    public Mono<LocalMember> createLocalMember(Member member){
        Objects.requireNonNull(member, "member");
        return Mono.defer(() -> {
            LocalMember localMember = new LocalMember();
            localMember.userId(member.getId());
            localMember.guildId(member.getGuildId());
            localMember.effectiveName(member.getDisplayName());
            Activity activity = new Activity();
            activity.guildId(member.getGuildId());
            localMember.activity(activity); // TODO: lazy initializing?
            return save(localMember).thenReturn(localMember);
        });
    }

    @Override
    public Mono<MessageInfo> createMessageInfo(Message message){
        Objects.requireNonNull(message, "message");
        return Mono.defer(() -> {
            MessageInfo messageInfo = new MessageInfo();
            messageInfo.messageId(message.getId());
            messageInfo.userId(message.getAuthor().map(User::getId).orElseThrow(IllegalStateException::new)); // only users, not webhooks
            messageInfo.guildId(message.getGuildId().orElseThrow(IllegalStateException::new)); // only guilds
            messageInfo.timestamp(message.getTimestamp());
            messageInfo.content(messageService.encrypt(MessageUtil.effectiveContent(message), message.getId(), message.getChannelId()));
            return save(messageInfo).thenReturn(messageInfo);
        });
    }

    @Override
    public Mono<StarboardConfig> createStarboardConfig(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return Mono.defer(() -> {
            StarboardConfig starboardConfig = new StarboardConfig();
            starboardConfig.guildId(guildId);
            starboardConfig.lowerStarBarrier(settings.getDefaults().getStarboardLowerStarBarrier());
            starboardConfig.emojis(defaultStarsEmojis);
            return save(starboardConfig).thenReturn(starboardConfig);
        });
    }

    @Override
    public Mono<ActivityConfig> createActiveUserConfig(Snowflake guildId){
        Objects.requireNonNull(guildId, "guildId");
        return Mono.defer(() -> {
            ActivityConfig activityConfig = new ActivityConfig();
            activityConfig.guildId(guildId);
            activityConfig.keepCountingDuration(settings.getDefaults().getActiveUserKeepCountingDuration());
            activityConfig.messageBarrier(settings.getDefaults().getActiveUserMessageBarrier());
            return save(activityConfig).thenReturn(activityConfig);
        });
    }

    @Override
    public Mono<Starboard> createStarboard(Snowflake guildId, Snowflake sourceMessageId, Snowflake targetMessageId){
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(sourceMessageId, "sourceMessageId");
        Objects.requireNonNull(targetMessageId, "targetMessageId");
        return Mono.defer(() -> {
            Starboard starboard = new Starboard();
            starboard.guildId(guildId);
            starboard.sourceMessageId(sourceMessageId);
            starboard.targetMessageId(targetMessageId);
            return save(starboard).thenReturn(starboard);
        });
    }

    @Override
    public Mono<EmojiDispenser> createEmojiDispenser(Snowflake guildId, Snowflake messageId, Snowflake roleId, EmojiData emojiData){
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(emojiData, "emojiData");
        return Mono.defer(() -> {
            EmojiDispenser emojiDispenser = new EmojiDispenser();
            emojiDispenser.guildId(guildId);
            emojiDispenser.messageId(messageId);
            emojiDispenser.roleId(roleId);
            emojiDispenser.emoji(emojiData);
            return save(emojiDispenser).thenReturn(emojiDispenser);
        });
    }
}
