package inside.data.type;

import inside.data.type.descriptor.JodaDurationTypeDescriptor;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.*;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;
import org.joda.time.Duration;

public class JodaDurationType extends AbstractSingleColumnStandardBasicType<Duration> implements LiteralType<Duration>{

    public static final JodaDurationType instance = new JodaDurationType();

    public JodaDurationType(){
        super(VarcharTypeDescriptor.INSTANCE, JodaDurationTypeDescriptor.instance);
    }

    @Override
    public String getName(){
        return "joda-duration";
    }

    @Override
    protected boolean registerUnderJavaType(){
        return true;
    }

    @Override
    public String objectToSQLString(Duration value, Dialect dialect) throws Exception{
        return StringType.INSTANCE.objectToSQLString(value.toString(), dialect);
    }
}
