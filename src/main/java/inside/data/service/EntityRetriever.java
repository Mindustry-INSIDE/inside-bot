package inside.data.service;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.*;
import discord4j.discordjson.json.EmojiData;
import inside.data.entity.*;
import reactor.core.publisher.*;

public interface EntityRetriever{

    // guild config

    Mono<GuildConfig> getGuildConfigById(Snowflake guildId);

    Mono<Void> save(GuildConfig guildConfig);

    Mono<Void> deleteGuildConfigById(Snowflake guildId);

    // admin config

    Mono<AdminConfig> getAdminConfigById(Snowflake guildId);

    Mono<Void> save(AdminConfig adminConfig);

    Mono<Void> deleteAdminConfigById(Snowflake guildId);

    // audit config

    Mono<AuditConfig> getAuditConfigById(Snowflake guildId);

    Mono<Void> save(AuditConfig auditConfig);

    Mono<Void> deleteAuditConfigById(Snowflake guildId);

    // member

    Flux<LocalMember> getAllLocalMembers();

    Mono<LocalMember> getLocalMemberById(Snowflake userId, Snowflake guildId);

    Mono<LocalMember> getAndUpdateLocalMemberById(Member member);

    Mono<Void> save(LocalMember localMember);

    Mono<Void> deleteAllLocalMembersInGuild(Snowflake guildId);

    // message info

    Mono<MessageInfo> getMessageInfoById(Snowflake messageId);

    Mono<Void> save(MessageInfo messageInfo);

    Mono<Void> delete(MessageInfo messageInfo);

    Mono<Void> deleteMessageInfoById(Snowflake messageId);

    Mono<Void> deleteAllMessageInfoInGuild(Snowflake guildId);

    // starboard config

    Mono<StarboardConfig> getStarboardConfigById(Snowflake guildId);

    Mono<Void> save(StarboardConfig starboardConfig);

    Mono<Void> deleteStarboardConfigById(Snowflake guildId);

    // starboard

    Mono<Starboard> getStarboardById(Snowflake guildId, Snowflake sourceMessageId);

    Mono<Void> deleteStarboardById(Snowflake guildId, Snowflake sourceMessageId);

    Mono<Void> save(Starboard starboard);

    Mono<Void> delete(Starboard starboard);

    Mono<Void> deleteAllStarboardsInGuild(Snowflake guildId);

    // active user config

    Mono<ActiveUserConfig> getActiveUserConfigById(Snowflake guildId);

    Mono<Void> save(ActiveUserConfig activeUserConfig);

    Mono<Void> deleteActiveUserConfigById(Snowflake guildId);

    // emoji dispenser

    Mono<EmojiDispenser> getEmojiDispenserById(Snowflake messageId);

    Mono<Void> save(EmojiDispenser emojiDispenser);

    Mono<Void> delete(EmojiDispenser emojiDispenser);

    Mono<Void> deleteAllEmojiDispenserInGuild(Snowflake guildId);

    // factory methods

    Mono<GuildConfig> createGuildConfig(Snowflake guildId);

    Mono<AdminConfig> createAdminConfig(Snowflake guildId);

    Mono<AuditConfig> createAuditConfig(Snowflake guildId);

    Mono<LocalMember> createLocalMember(Member member);

    Mono<ActiveUserConfig> createActiveUserConfig(Snowflake guildId);

    Mono<MessageInfo> createMessageInfo(Message message);

    Mono<StarboardConfig> createStarboardConfig(Snowflake guildId);

    Mono<Starboard> createStarboard(Snowflake guildId, Snowflake sourceMessageId, Snowflake targetMessageId);

    Mono<EmojiDispenser> createEmojiDispenser(Snowflake guildId, Snowflake messageId, Snowflake roleId, EmojiData emojiData);
}
