package inside.data.type.descriptor;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.joda.time.DateTimeZone;
import reactor.util.annotation.Nullable;

public class DateTimeZoneTypeDescriptor extends AbstractTypeDescriptor<DateTimeZone>{

    public static final DateTimeZoneTypeDescriptor instance = new DateTimeZoneTypeDescriptor();

    public DateTimeZoneTypeDescriptor(){
        super(DateTimeZone.class);
    }

    @Override
    public DateTimeZone fromString(String string){
        return DateTimeZone.forID(string);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X unwrap(@Nullable DateTimeZone value, Class<X> type, WrapperOptions options){
        if(value == null){
            return null;
        }
        if(String.class.isAssignableFrom(type)){
            return (X)toString(value);
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> DateTimeZone wrap(@Nullable X value, WrapperOptions options){
        if(value == null){
            return null;
        }
        if(value instanceof String s){
            return fromString(s);
        }
        throw unknownWrap(value.getClass());
    }
}

