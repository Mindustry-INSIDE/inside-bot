package inside.audit;

import discord4j.common.util.Snowflake;
import discord4j.core.object.Embed;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.*;
import inside.data.entity.AuditAction;
import inside.data.entity.base.NamedReference;
import inside.util.*;
import reactor.util.context.ContextView;

import java.time.Instant;
import java.time.format.*;

import static inside.audit.Attribute.*;
import static inside.util.ContextUtil.*;

public class AuditProviders{

    private AuditProviders(){}

    @ForwardAuditProvider(AuditActionType.MESSAGE_EDIT)
    public static class MessageEditAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            Snowflake messageId = action.getAttribute(MESSAGE_ID);
            String oldContent = action.getAttribute(OLD_CONTENT);
            String newContent = action.getAttribute(NEW_CONTENT);
            String url = action.getAttribute(AVATAR_URL);
            if(messageId == null || oldContent == null || newContent == null || url == null){
                return;
            }

            embed.setAuthor(formatName(action.user()), null, url);
            embed.setDescription(messageService.format(context, "audit.message.edit.description",
                    action.guildId().asString(),
                    action.channel().id(),
                    messageId.asString()));

            if(oldContent.length() > 0){
                embed.addField(messageService.get(context, "audit.message.old-content.title"),
                        MessageUtil.substringTo(oldContent, Embed.Field.MAX_VALUE_LENGTH), false);
            }

            if(newContent.length() > 0){
                embed.addField(messageService.get(context, "audit.message.new-content.title"),
                        MessageUtil.substringTo(newContent, Embed.Field.MAX_VALUE_LENGTH), true);
            }

            embed.addField(messageService.get(context, "audit.message.channel"),
                    getChannelReference(context, action.channel()), false);

            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MESSAGE_DELETE)
    public static class MessageDeleteAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String oldContent = action.getAttribute(OLD_CONTENT);
            String url = action.getAttribute(AVATAR_URL);
            NamedReference target = action.target();
            if(oldContent == null || url == null || target == null){
                return;
            }

            embed.setAuthor(formatName(target), null, url);

            if(oldContent.length() > 0){
                embed.addField(messageService.get(context, "audit.message.deleted-content.title"),
                        MessageUtil.substringTo(oldContent, Embed.Field.MAX_VALUE_LENGTH), true);
            }

            embed.addField(messageService.get(context, "audit.message.channel"),
                    getChannelReference(context, action.channel()), false);

            if(!action.user().equals(target)){
                embed.addField(messageService.get(context, "audit.message.responsible-user"),
                        getUserReference(context, action.user()), false);
            }

            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MESSAGE_CLEAR)
    public static class MessageClearAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            Long count = action.getAttribute(COUNT);
            if(count == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.message.clear.description", count,
                    messageService.getCount(context, "common.plurals.message", count)));
            embed.addField(messageService.get(context, "audit.member.admin"),
                    getUserReference(context, action.user()), true);
            embed.addField(messageService.get(context, "audit.message.channel"),
                    getChannelReference(context, action.channel()), false);
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_JOIN)
    public static class MemberJoinAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            embed.setDescription(messageService.format(context, "audit.member.join.description",
                    getUserReference(context, action.user())));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_LEAVE)
    public static class MemberLeaveAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            embed.setDescription(messageService.format(context, "audit.member.leave.description",
                    getUserReference(context, action.user())));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_KICK)
    public static class MemberKickAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String reason = action.getAttribute(REASON);
            NamedReference target = action.target();
            if(target == null || reason == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.member.kick.title", getUserReference(context, target)));
            embed.addField(messageService.get(context, "audit.member.admin"),
                    getUserReference(context, action.user()), true);
            embed.addField(messageService.get(context, "audit.member.reason"), reason, true);
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_BAN)
    public static class MemberBanAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String reason = action.getAttribute(REASON);
            NamedReference target = action.target();
            if(target == null || reason == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.member.ban.title", getUserReference(context, target)));
            embed.addField(messageService.get(context, "audit.member.admin"),
                    getUserReference(context, action.user()), true);
            embed.addField(messageService.get(context, "audit.member.reason"), reason, true);
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_UNMUTE)
    public static class MemberUnmuteAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            NamedReference target = action.target();
            if(target == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.member.unmute.title", getUserReference(context, target)));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_MUTE)
    public static class MemberMuteAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            Instant delay = action.getAttribute(DELAY);
            String reason = action.getAttribute(REASON);
            NamedReference target = action.target();
            if(reason == null){
                reason = messageService.get(context, "common.not-defined");
            }

            if(delay == null || target == null){
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(context.get(KEY_LOCALE))
                    .withZone(context.get(KEY_TIMEZONE));

            embed.setDescription(messageService.format(context, "audit.member.mute.title", getUserReference(context, target)));
            embed.addField(messageService.get(context, "audit.member.admin"),
                    getUserReference(context, action.user()), true);
            embed.addField(messageService.get(context, "audit.member.reason"), reason, true);
            embed.addField(messageService.get(context, "audit.member.mute.delay"),
                    formatter.format(delay), true);
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_ROLE_ADD)
    public static class MemberRoleAddAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String url = action.getAttribute(AVATAR_URL);
            Snowflake roleId = action.getAttribute(ROLE_ID);
            if(url == null || roleId == null){
                return;
            }

            embed.setAuthor(formatName(action.user()), null, url);
            embed.setDescription(messageService.format(context, "audit.member.role-add.title",
                    DiscordUtil.getRoleMention(roleId), getUserReference(context, action.user())));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_ROLE_REMOVE)
    public static class MemberRoleRemoveAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String url = action.getAttribute(AVATAR_URL);
            Snowflake roleId = action.getAttribute(ROLE_ID);
            if(url == null || roleId == null){
                return;
            }

            embed.setAuthor(formatName(action.user()), null, url);
            embed.setDescription(messageService.format(context, "audit.member.role-remove.title",
                    DiscordUtil.getRoleMention(roleId), getUserReference(context, action.user())));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_AVATAR_UPDATE)
    public static class MemberAvatarUpdateAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String url = action.getAttribute(AVATAR_URL);
            String oldUrl = action.getAttribute(OLD_AVATAR_URL);
            if(oldUrl == null || url == null){
                return;
            }

            embed.setAuthor(formatName(action.user()), null, url);
            embed.setDescription(messageService.format(context, "audit.member.avatar-update.title",
                    getUserReference(context, action.user())));
            embed.setThumbnail(oldUrl);
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.MEMBER_NICKNAME_UPDATE)
    public static class MemberNicknameUpdate extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            String url = action.getAttribute(AVATAR_URL);
            String oldNickname = action.getAttribute(OLD_NICKNAME);
            String newNickname = action.getAttribute(NEW_NICKNAME);
            if(url == null || oldNickname == null || newNickname == null){
                return;
            }

            embed.setAuthor(formatName(action.user()), null, url);
            embed.setDescription(messageService.format(context, "audit.member.nickname-update.title",
                    getUserReference(context, action.user()),
                    oldNickname, newNickname));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.REACTION_ADD)
    public static class ReactionAddAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            Snowflake messageId = action.getAttribute(MESSAGE_ID);
            ReactionEmoji emoji = action.getAttribute(REACTION_EMOJI);
            if(messageId == null || emoji == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.reaction.add.description",
                    getUserReference(context, action.user()), DiscordUtil.getEmojiString(emoji),
                    action.guildId().asString(), action.channel().id(), messageId.asString()));

            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.REACTION_REMOVE)
    public static class ReactionRemoveAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            Snowflake messageId = action.getAttribute(MESSAGE_ID);
            ReactionEmoji emoji = action.getAttribute(REACTION_EMOJI);
            if(messageId == null || emoji == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.reaction.remove.description",
                    DiscordUtil.getEmojiString(emoji), getUserReference(context, action.user()),
                    action.guildId().asString(), action.channel().id(), messageId.asString()));

            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.REACTION_REMOVE_ALL)
    public static class ReactionRemoveAllAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            Snowflake messageId = action.getAttribute(MESSAGE_ID);
            if(messageId == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.reaction.remove-all.description",
                    action.guildId().asString(), action.channel().id(), messageId.asString()));

            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.VOICE_JOIN)
    public static class VoiceJoinAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            embed.setDescription(messageService.format(context, "audit.voice.join.description",
                    getUserReference(context, action.user()), getShortReference(context, action.channel())));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.VOICE_LEAVE)
    public static class VoiceLeaveAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            embed.setDescription(messageService.format(context, "audit.voice.leave.description",
                    getUserReference(context, action.user()), getShortReference(context, action.channel())));
            addTimestamp(context, action, embed);
        }
    }

    @ForwardAuditProvider(AuditActionType.VOICE_MOVE)
    public static class VoiceMoveAuditProvider extends BaseAuditProvider{
        @Override
        protected void build(AuditAction action, ContextView context, MessageCreateSpec spec, EmbedCreateSpec embed){
            NamedReference oldChannel = action.getAttribute(OLD_CHANNEL);
            if(oldChannel == null){
                return;
            }

            embed.setDescription(messageService.format(context, "audit.voice.move.description",
                    getUserReference(context, action.user()),
                    getShortReference(context, oldChannel),
                    getShortReference(context, action.channel())));
            addTimestamp(context, action, embed);
        }
    }
}
