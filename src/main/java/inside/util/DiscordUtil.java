package inside.util;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.discordjson.json.EmojiData;
import reactor.util.annotation.Nullable;

import java.util.Objects;

public abstract class DiscordUtil{

    private DiscordUtil(){

    }

    public static boolean isBot(@Nullable User user){
        return user == null || user.isBot();
    }

    public static boolean isNotBot(@Nullable User user){
        return !isBot(user);
    }

    public static String getEmojiString(ReactionEmoji emoji){
        Objects.requireNonNull(emoji, "emoji");
        return emoji.asUnicodeEmoji().map(ReactionEmoji.Unicode::getRaw)
                .orElseGet(() -> emoji.asCustomEmoji()
                        .map(ReactionEmoji.Custom::asFormat)
                        .orElseThrow(IllegalStateException::new));
    }

    public static String getEmojiString(EmojiData data){
        Objects.requireNonNull(data, "data");
        String name = data.name().orElseThrow(IllegalArgumentException::new);
        if(data.id().isPresent()){
            return String.format("<%s:%s:%s>", data.animated().toOptional()
                            .map(bool -> bool ? "a" : "").orElse(""),
                    name, data.id().get());
        }
        return name;
    }

    public static String getUserMention(Snowflake id){
        Objects.requireNonNull(id, "id");
        return "<@" + id.asString() + ">";
    }

    public static String getMemberMention(Snowflake id){
        Objects.requireNonNull(id, "id");
        return "<@!" + id.asString() + ">";
    }

    public static String getChannelMention(Snowflake id){
        Objects.requireNonNull(id, "id");
        return "<#" + id.asString() + ">";
    }

    public static String getRoleMention(Snowflake id){
        Objects.requireNonNull(id, "id");
        return "<@&" + id.asString() + ">";
    }
}
