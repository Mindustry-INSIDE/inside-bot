package inside.util.json;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import discord4j.common.util.Snowflake;
import discord4j.core.object.reaction.ReactionEmoji;

import java.io.Serial;

public class AdapterModule extends SimpleModule{
    @Serial
    private static final long serialVersionUID = 2905663996606216982L;

    public AdapterModule(){
        setMixInAnnotation(Snowflake.class, SnowflakeMixin.class);
        setMixInAnnotation(ReactionEmoji.Custom.class, CustomReactionEmojiMixin.class);
        setMixInAnnotation(ReactionEmoji.Unicode.class, UnicodeReactionEmojiMixin.class);
    }

    @Override
    public String getModuleName(){
        return "AdapterModule";
    }

    static abstract class SnowflakeMixin{
        @JsonValue
        abstract String asString();
    }

    static abstract class CustomReactionEmojiMixin{

        CustomReactionEmojiMixin(@JsonProperty("id") long id,
                                 @JsonProperty("name") String name,
                                 @JsonProperty("isAnimated") boolean isAnimated){
        }
    }

    static abstract class UnicodeReactionEmojiMixin{

        UnicodeReactionEmojiMixin(@JsonProperty String raw){
        }

        @JsonValue
        abstract String getRaw();
    }
}
