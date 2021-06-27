package inside.audit;

import discord4j.common.util.Snowflake;
import discord4j.core.object.reaction.ReactionEmoji;
import inside.data.entity.base.NamedReference;

import java.time.Instant;
import java.util.Objects;

public final class Attribute<T>{

    public static final Attribute<String> OLD_CONTENT = new Attribute<>("old_content");

    public static final Attribute<String> NEW_CONTENT = new Attribute<>("new_content");

    public static final Attribute<String> REASON = new Attribute<>("reason");

    public static final Attribute<Instant> DELAY = new Attribute<>("delay");

    public static final Attribute<String> AVATAR_URL = new Attribute<>("avatar_url");

    public static final Attribute<String> OLD_AVATAR_URL = new Attribute<>("old_avatar_url");

    public static final Attribute<Snowflake> MESSAGE_ID = new Attribute<>("message_id");

    public static final Attribute<Long> COUNT = new Attribute<>("count");

    public static final Attribute<ReactionEmoji> REACTION_EMOJI = new Attribute<>("reaction_emoji");

    public static final Attribute<Snowflake> ROLE_ID = new Attribute<>("role_id");

    public static final Attribute<NamedReference> OLD_CHANNEL = new Attribute<>("old_channel");

    public final String name;

    private Attribute(String name){
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Attribute<?> attribute = (Attribute<?>)o;
        return name.equals(attribute.name);
    }

    @Override
    public int hashCode(){
        return Objects.hash(name);
    }

    @Override
    public String toString(){
        return "Attribute{" + name + '}';
    }
}
