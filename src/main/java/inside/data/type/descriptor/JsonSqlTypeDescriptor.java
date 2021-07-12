package inside.data.type.descriptor;

import org.hibernate.type.descriptor.*;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.*;

import java.io.Serial;
import java.sql.*;

public class JsonSqlTypeDescriptor implements SqlTypeDescriptor{
    @Serial
    private static final long serialVersionUID = -8941996480499577393L;

    public static final JsonSqlTypeDescriptor instance = new JsonSqlTypeDescriptor();

    @Override
    public int getSqlType(){
        return Types.OTHER;
    }

    @Override
    public boolean canBeRemapped(){
        return true;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> descriptor){
        return new BasicExtractor<>(descriptor, this){
            @Override
            protected X doExtract(ResultSet resultSet, String name, WrapperOptions options) throws SQLException{
                return descriptor.wrap(resultSet.getString(name), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException{
                return descriptor.wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException{
                return descriptor.wrap(statement.getString(name), options);
            }
        };
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> descriptor){
        return new BasicBinder<>(descriptor, this){
            @Override
            protected void doBind(PreparedStatement statement, X value, int index, WrapperOptions options) throws SQLException{
                statement.setObject(index, descriptor.unwrap(value, String.class, options), getSqlType());
            }

            @Override
            protected void doBind(CallableStatement statement, X value, String name, WrapperOptions options) throws SQLException{
                statement.setObject(name, descriptor.unwrap(value, String.class, options), getSqlType());
            }
        };
    }
}
