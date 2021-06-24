package inside.command.model;

import inside.util.Strings;
import reactor.util.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class CommandOption{
    private final CommandParam param;
    @Nullable
    private final String value;

    public CommandOption(CommandParam param, @Nullable String value){
        this.param = Objects.requireNonNull(param, "param");
        this.value = value;
    }

    public String getName(){
        return param.name();
    }

    public boolean isOptional(){
        return param.optional();
    }

    public boolean isVariadic(){
        return param.variadic();
    }

    public Optional<OptionValue> getValue(){
        return Optional.ofNullable(value).filter(Strings::isNotEmpty).map(OptionValue::new);
    }

    public List<OptionValue> getChoices(){
        return Arrays.stream(param.name().split("[/|]"))
                .map(OptionValue::new)
                .collect(Collectors.toList());
    }

    public Optional<OptionValue> getChoice(String name){
        return getChoices().stream()
                .filter(option -> option.asString().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<OptionValue> getChoice(){
        return value != null ? getChoice(value) : Optional.empty();
    }

    @Override
    public String toString(){
        return "CommandOption{" +
                "param=" + param +
                ", value='" + value + '\'' +
                '}';
    }
}
