package inside.data.type.descriptor;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;

import java.time.ZoneId;
import java.util.Comparator;

public class ZoneIdTypeDescriptor extends AbstractTypeDescriptor<ZoneId>{
    public static final ZoneIdTypeDescriptor instance = new ZoneIdTypeDescriptor();

    public ZoneIdTypeDescriptor(){
        super(ZoneId.class);
    }

    @Override
    public String toString(ZoneId value){
        return value.getId();
    }

    @Override
    public ZoneId fromString(String string){
        return ZoneId.of(string);
    }

    @Override
    public Comparator<ZoneId> getComparator(){
        return Comparator.comparing(ZoneId::getId);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <X> X unwrap(ZoneId value, Class<X> type, WrapperOptions options){
        if(value == null){
            return null;
        }
        if(String.class.isAssignableFrom(type)){
            return (X)toString(value);
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> ZoneId wrap(X value, WrapperOptions options){
        if(value == null){
            return null;
        }
        if(value instanceof String s){
            return fromString(s);
        }
        throw unknownWrap(value.getClass());
    }
}
