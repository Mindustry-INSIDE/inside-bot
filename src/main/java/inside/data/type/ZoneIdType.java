package inside.data.type;

import inside.data.type.descriptor.ZoneIdTypeDescriptor;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.*;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import java.time.ZoneId;

public class ZoneIdType extends AbstractSingleColumnStandardBasicType<ZoneId> implements LiteralType<ZoneId>{

    public static final ZoneIdType instance = new ZoneIdType();

    public ZoneIdType(){
        super(VarcharTypeDescriptor.INSTANCE, ZoneIdTypeDescriptor.instance);
    }

    @Override
    public String getName(){
        return "zone_id";
    }

    @Override
    protected boolean registerUnderJavaType(){
        return true;
    }

    @Override
    public String objectToSQLString(ZoneId value, Dialect dialect) throws Exception{
        return StringType.INSTANCE.objectToSQLString(value.getId(), dialect);
    }
}
