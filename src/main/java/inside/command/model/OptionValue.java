package inside.command.model;

import discord4j.common.util.Snowflake;
import inside.util.*;
import reactor.util.annotation.Nullable;

import java.time.ZonedDateTime;
import java.util.Objects;

public class OptionValue{
    private final String value;

    public OptionValue(String value){
        this.value = Objects.requireNonNull(value, "value");
    }

    public String asString(){
        return value;
    }

    public long asLong(){
        return Strings.parseLong(value);
    }

    public boolean asBoolean(){
        return Boolean.parseBoolean(value);
    }

    @Nullable
    public Snowflake asSnowflake(){
        return MessageUtil.parseId(value.replaceAll("[<>@!#&]", "")); // parse all possible id types
    }

    @Nullable
    public ZonedDateTime asDateTime(){
        return Try.ofCallable(() -> MessageUtil.parseTime(value)).orElse(null);
    }

    @Override
    public String toString(){
        return "OptionValue{" +
                "value='" + value + '\'' +
                '}';
    }
}
