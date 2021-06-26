package inside.data.type.descriptor;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.*;

import java.time.Duration;

public class DurationDescriptor extends AbstractTypeDescriptor<Duration>{

    public static final DurationDescriptor instance = new DurationDescriptor();

    @SuppressWarnings("unchecked")
    public DurationDescriptor(){
        super(Duration.class, ImmutableMutabilityPlan.INSTANCE);
    }

    @Override
    public String toString(Duration value){
        if(value == null){
            return null;
        }
        return value.toString();
    }

    @Override
    public Duration fromString(String string){
        if(string == null){
            return null;
        }
        return Duration.parse(string);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <X> X unwrap(Duration duration, Class<X> type, WrapperOptions options){
        if(duration == null){
            return null;
        }

        if(Duration.class.isAssignableFrom(type)){
            return (X)duration;
        }

        if(String.class.isAssignableFrom(type)){
            return (X)duration.toString();
        }

        if(Long.class.isAssignableFrom(type)){
            return (X)Long.valueOf(duration.toNanos());
        }

        throw unknownUnwrap(type);
    }

    @Override
    public <X> Duration wrap(X value, WrapperOptions options){
        if(value == null){
            return null;
        }

        if(value instanceof Duration d){
            return d;
        }

        if(value instanceof Long l){
            return Duration.ofNanos(l);
        }

        if(value instanceof String s){
            return Duration.parse(s);
        }

        throw unknownWrap(value.getClass());
    }
}
