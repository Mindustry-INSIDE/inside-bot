package inside.command.model;

public record CommandParam(String name, boolean optional, boolean variadic){

    public static final CommandParam[] empty = {};
}
