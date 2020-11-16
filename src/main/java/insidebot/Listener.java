package insidebot;

import arc.Events;
import arc.files.Fi;
import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.guild.*;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.*;
import discord4j.rest.util.Color;
import insidebot.EventType.*;
import insidebot.data.dao.MessageInfoDao;
import insidebot.data.dao.UserInfoDao;
import insidebot.data.model.MessageInfo;
import insidebot.data.model.UserInfo;
import reactor.util.annotation.NonNull;

import java.util.*;
import java.util.function.Consumer;

import static insidebot.AuditEventType.*;
import static insidebot.InsideBot.*;

public class Listener{
    public Guild guild;
    public DiscordClient client;
    public GatewayDiscordClient gateway;
    public Color normalColor = Color.of(0xC4F5B7), errorColor = Color.of(0xff3838);
    public Fi temp = new Fi("message.txt");

    // привет костыль (на самом деле не совсем то и костыль)
    public ObjectSet<Snowflake> buffer = new ObjectSet<>();

    TextChannel channel;
    User lastUser;
    Message lastMessage, lastSentMessage;

    // Регистрируем ивентики
    protected void register(){
        listener.guild = listener.gateway.getGuildById(guildID).block();

        gateway.on(MessageCreateEvent.class).subscribe(event -> {
            User user = event.getMessage().getAuthor().get();
            Message message = event.getMessage();
            if(user.isBot()) return;
            MessageInfo info = new MessageInfo();
            UserInfo userInfo = UserInfoDao.getOr(user.getId(), UserInfo::new);

            userInfo.setName(user.getUsername());
            userInfo.setUserId(user.getId().asLong());
            userInfo.setLastSentMessage(Calendar.getInstance());
            userInfo.addToSeq();

            info.setUser(userInfo);
            info.setMessageId(message.getId().asLong());
            info.setChannelId(message.getChannelId().asLong());

            info.setContent(effectiveContent(message));
            userInfo.getMessageInfo().add(info);

            commands.handle(event);
            UserInfoDao.saveOrUpdate(userInfo);
        }, Log::err);

        gateway.on(MessageUpdateEvent.class).subscribe(event -> {
            Message message = event.getMessage().block();
            User user = message.getAuthor().get();
            if(user.isBot()) return;

            EmbedCreateSpec embed = new EmbedCreateSpec();
            MessageInfo info = MessageInfoDao.get(event.getMessageId());

            if(info == null) return;

            String oldContent = info.getContent();
            String newContent = effectiveContent(message);
            int maxLength = 1024;
            boolean write = newContent.length() >= maxLength || oldContent.length() >= maxLength;

            if(message.isPinned() || newContent.equals(oldContent)) return;

            embed.setColor(messageEdit.color);
            embed.setAuthor(memberedName(user), null, user.getAvatarUrl());
            embed.setTitle(bundle.format("message.edit", event.getChannel().block().getMention()));
            embed.setDescription(bundle.format("message.edit.description", event.getGuildId().get().asLong(),
                                               event.getChannelId().asLong(), event.getMessageId().asLong()));

            embed.addField(bundle.get("message.edit.old-content"),
                           MessageUtil.substringTo(oldContent, maxLength), false);
            embed.addField(bundle.get("message.edit.new-content"),
                           MessageUtil.substringTo(newContent, maxLength), true);

            embed.setFooter(data.zonedFormat(), null);

            if(write){
                temp.writeString(String.format("%s\n%s\n\n%s\n%s", bundle.get("message.edit.old-content"), oldContent,
                                               bundle.get("message.edit.new-content"), newContent));
            }

            log(embed, write);

            info.setContent(newContent);
            MessageInfoDao.update(info);
        }, Log::err);

        gateway.on(MessageDeleteEvent.class).subscribe(event -> {
            if(!MessageInfoDao.exists(event.getMessageId())) return;
            if(buffer.contains(event.getMessageId())){
                buffer.remove(event.getMessageId());
                return;
            }

            EmbedCreateSpec embed = new EmbedCreateSpec();
            MessageInfo info = MessageInfoDao.get(event.getMessageId());

            User user = info.getUser().asUser(); // Nonnull
            String content = info.getContent();
            int maxLength = 1024;

            boolean under = content.length() >= maxLength;

            if(content.isEmpty()) return;

            embed.setColor(messageDelete.color);
            embed.setAuthor(memberedName(user), null, user.getAvatarUrl());
            embed.setTitle(bundle.format("message.delete", event.getChannel().block().getMention()));
            embed.setFooter(data.zonedFormat(), null);

            embed.addField(bundle.get("message.delete.content"), under ? MessageUtil.substringTo(content, maxLength)
                                                                       : content, true);

            log(embed, under);

            MessageInfoDao.remove(info);
        }, Log::err);

        gateway.on(VoiceStateUpdateEvent.class).subscribe(event -> {
            VoiceChannel channel = event.getCurrent().getChannel().block();
            User user = event.getCurrent().getUser().block();
            if(user == null || user.isBot() || channel == null) return;
            log(embedBuilder -> {
                embedBuilder.setColor(voiceJoin.color);
                embedBuilder.setTitle(bundle.get("message.voice-join"));
                embedBuilder.setDescription(bundle.format("message.voice-join.text", memberedName(user), channel.getName()));
                embedBuilder.setFooter(data.zonedFormat(), null);
            });
        }, Log::err);

        gateway.on(VoiceStateUpdateEvent.class).subscribe(event -> {
            VoiceChannel channel = event.getOld().get().getChannel().block();
            User user = event.getOld().get().getUser().block();
            if(user == null || user.isBot() || channel == null) return;
            log(embedBuilder -> {
                embedBuilder.setColor(voiceLeave.color);
                embedBuilder.setTitle(bundle.get("message.voice-leave"));
                embedBuilder.setDescription(bundle.format("message.voice-leave.text", memberedName(user), channel.getName()));
                embedBuilder.setFooter(data.zonedFormat(), null);
            });
        }, Log::err);

        gateway.on(MemberJoinEvent.class).subscribe(event -> {
            User user = gateway.getUserById(event.getMember().getId()).block();
            if(user == null || user.isBot()) return;
            log(embedBuilder -> {
                embedBuilder.setColor(userJoin.color);
                embedBuilder.setTitle(bundle.get("message.user-join"));
                embedBuilder.setDescription(bundle.format("message.user-join.text", user.getUsername()));
                embedBuilder.setFooter(data.zonedFormat(), null);
            });
        }, Log::err);

        gateway.on(MemberLeaveEvent.class).subscribe(event -> {
            User user = event.getUser();
            if(user.isBot()) return;
            UserInfoDao.removeById(user.getId());
            log(embedBuilder -> {
                embedBuilder.setColor(userLeave.color);
                embedBuilder.setTitle(bundle.get("message.user-leave"));
                embedBuilder.setDescription(bundle.format("message.user-leave.text", user.getUsername()));
                embedBuilder.setFooter(data.zonedFormat(), null);
            });
        }, Log::err);

        gateway.on(BanEvent.class).subscribe(event -> {
            User user = event.getUser();
            if(user.isBot()) return;
            UserInfoDao.removeById(user.getId());
            log(embedBuilder -> {
                embedBuilder.setColor(userBan.color);
                embedBuilder.setTitle(bundle.get("message.ban"));
                embedBuilder.setDescription(bundle.format("message.ban.text", user.getUsername()));
                embedBuilder.setFooter(data.zonedFormat(), null);
            });
        }, Log::err);

        gateway.on(MemberUpdateEvent.class).subscribe(event -> {
            User user = gateway.getUserById(event.getMemberId()).block();
            if(user == null || user.isBot()) return;
            UserInfo info = UserInfoDao.get(user.getId());
            if(info == null) return;
            info.setName(event.getCurrentNickname().get());
            UserInfoDao.update(info);
        }, Log::err);

        // Внутренние ивенты

        Events.on(MemberUnmuteEvent.class, event -> {
            Member member = event.userInfo.asMember();
            if(member == null) return;

            event.userInfo.setMuteEndDate(null);
            UserInfoDao.update(event.userInfo);
            member.removeRole(muteRoleID).block();
            log(e -> {
                e.setTitle(bundle.get("message.unmute"));
                e.setDescription(bundle.format("message.unmute.text", event.userInfo.getName()));
                e.setFooter(data.zonedFormat(), null);
                e.setColor(userUnmute.color);
            });
        });

        Events.on(MemberMuteEvent.class, event -> {
            Member member = guild.getMemberById(event.user.getId()).block();
            UserInfo userInfo = UserInfoDao.get(event.user.getId());
            if(member == null || userInfo == null) return;

            Calendar calendar = Calendar.getInstance();
            calendar.roll(Calendar.DAY_OF_YEAR, +event.delay);
            userInfo.setMuteEndDate(calendar);
            UserInfoDao.update(userInfo);
            member.addRole(muteRoleID).block();
            log(embedBuilder -> {
                embedBuilder.setTitle(bundle.get("message.mute"));
                embedBuilder.setDescription(bundle.format("message.mute.text", event.user.getMention(), event.delay));
                embedBuilder.setFooter(data.zonedFormat(), null);
                embedBuilder.setColor(userMute.color);
            });
        });

        Events.on(MessageClearEvent.class, event -> {
            log(embedBuilder -> {
                String channel = event.history.get(0).getChannel().block().getMention();
                embedBuilder.setTitle(bundle.format("message.clear", event.count, channel));
                embedBuilder.setDescription(bundle.format("message.clear.text", event.user.getUsername(), event.count, channel));
                embedBuilder.setFooter(data.zonedFormat(), null);
                embedBuilder.setColor(messageClear.color);

                StringBuilder builder = new StringBuilder();
                event.history.forEach(m -> {
                    buffer.add(m.getId());
                    builder.append('[').append(m.getTimestamp()).append("] ");
                    builder.append(m.getUserData().username()).append(" = ");
                    builder.append(m.getContent());
                    if(!m.getAttachments().isEmpty()){
                        builder.append("\n---\n");
                        m.getAttachments().forEach(a -> builder.append(a.getUrl()).append("\n"));
                    }
                    builder.append('\n');
                });
                temp.writeString(builder.toString());
            }, true);
        });

        Events.on(MemberBanEvent.class, event -> { // переадресирует на ивент от d4j. oh no кандидат на чистку
            guild.ban(Snowflake.of(event.userInfo.getUserId()), b -> b.setDeleteMessageDays(0)).block();
            UserInfoDao.remove(event.userInfo);
        });
    }

    // utils

    public void text(String text, Object... args){
        lastSentMessage = channel.createMessage(Strings.format(text, args)).block();
    }

    public void info(String title, String text, Object... args){
        MessageCreateSpec m = new MessageCreateSpec().setEmbed(e -> e.setColor(normalColor).setTitle(title)
                                                                     .setDescription(Strings.format(text, args)));

        lastSentMessage = channel.createMessage(e -> e = m).block();
    }

    public void err(String text, Object... args){
        err(bundle.get("error"), text, args);
    }

    public void err(String title, String text, Object... args){
        MessageCreateSpec result = new MessageCreateSpec().setEmbed(e -> e.setColor(errorColor).setTitle(title)
                                                                          .setDescription(Strings.format(text, args)));

        lastSentMessage = channel.createMessage(m -> m = result).block();
    }

    public void log(MessageCreateSpec message){
        guild.getChannelById(logChannelID).cast(TextChannel.class).block()
             .getRestChannel().createMessage(message.asRequest()).block();
    }

    public void log(EmbedCreateSpec embed){
        log(e -> e = embed, false);
    }

    public void log(EmbedCreateSpec embed, boolean file){
        log(e -> e = embed, file);
    }

    public void log(Consumer<EmbedCreateSpec> embed, boolean file){
        MessageCreateSpec m = new MessageCreateSpec().setEmbed(embed);
        log(file ? m.addFile("message", temp.read()) : m);
    }

    public void log(Consumer<EmbedCreateSpec> embed){
        log(embed, false);
    }

    // username / membername
    public String memberedName(@NonNull User user){
        String name = user.getUsername();
        Member member = guild.getMemberById(user.getId()).block();
        if(member != null && member.getNickname().isPresent()){
            name += " / " + member.getNickname();
        }
        return name;
    }

    public String effectiveContent(@NonNull Message message){
        StringBuilder builder = new StringBuilder(message.getContent());
        if(!message.getAttachments().isEmpty()){
            builder.append("\n---\n");
            message.getAttachments().forEach(a -> builder.append(a.getUrl()).append("\n"));
        }
        return builder.toString();
    }
}
