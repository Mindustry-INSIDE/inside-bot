package inside.util;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import discord4j.discordjson.possible.*;
import inside.util.json.AdapterModule;

import java.lang.reflect.Type;

// TODO: delete and use JacksonResources
@SuppressWarnings("unchecked")
public abstract class JacksonUtil{
    private static final ObjectMapper mapper = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.CUSTOM,
                    JsonInclude.Include.ALWAYS, PossibleFilter.class, null))
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
            .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            .registerModule(new AdapterModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .registerModule(new PossibleModule());

    public static ObjectMapper mapper(){
        return mapper;
    }

    public static <T> T fromJson(String string, TypeReference<T> reference){
        try{
            return mapper.readValue(string, reference);
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    public static <T> T fromJson(String string, Class<T> clazz){
        try{
            return mapper.readValue(string, clazz);
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    public static <T> T fromJson(String string, Type type){
        try{
            return mapper.readValue(string, mapper.constructType(type));
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    public static String toJson(Object value){
        try{
            return mapper.writeValueAsString(value);
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    public static JsonNode toJsonNode(Object value){
        return toJsonNode(toJson(value));
    }

    public static JsonNode toJsonNode(String value){
        try{
            return mapper.readTree(value);
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    public static <T> T copy(T value){
        return fromJson(toJson(value), (Class<T>)value.getClass());
    }
}
