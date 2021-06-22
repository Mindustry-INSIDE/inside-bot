package inside.event;

import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import inside.data.service.EntityRetriever;
import inside.util.DiscordUtil;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RoleDispenserEventHandler extends ReactiveEventAdapter{

    @Autowired
    private EntityRetriever entityRetriever;

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        return entityRetriever.getEmojiDispensersById(event.getMessageId())
                .filter(emojiDispenser -> event.getEmoji().equals(DiscordUtil.toReactionEmoji(emojiDispenser.emoji())))
                .flatMap(emojiDispenser -> event.getUser().flatMap(user -> user.asMember(guildId))
                        .flatMap(member -> member.addRole(emojiDispenser.roleId())));
    }

    @Override
    public Publisher<?> onReactionRemove(ReactionRemoveEvent event){
        Snowflake guildId = event.getGuildId().orElse(null);
        if(guildId == null){
            return Mono.empty();
        }

        return entityRetriever.getEmojiDispensersById(event.getMessageId())
                .filter(emojiDispenser -> event.getEmoji().equals(DiscordUtil.toReactionEmoji(emojiDispenser.emoji())))
                .flatMap(emojiDispenser -> event.getUser().flatMap(user -> user.asMember(guildId))
                        .flatMap(member -> member.removeRole(emojiDispenser.roleId())));
    }
}
