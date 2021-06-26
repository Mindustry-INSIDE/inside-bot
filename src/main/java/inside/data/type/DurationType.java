package inside.data.type;

import inside.data.type.descriptor.DurationDescriptor;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.*;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import java.time.Duration;

public class DurationType extends AbstractSingleColumnStandardBasicType<Duration> implements LiteralType<Duration>{

    public static final DurationType instance = new DurationType();

    public DurationType(){
        super(VarcharTypeDescriptor.INSTANCE, DurationDescriptor.instance);
    }

    @Override
    public String getName(){
        return "jduration";
    }

    @Override
    protected boolean registerUnderJavaType(){
        return true;
    }

    @Override
    public String objectToSQLString(Duration value, Dialect dialect) throws Exception{
        return value.toString();
    }
}
