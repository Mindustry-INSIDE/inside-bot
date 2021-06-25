package inside.data.type;

import inside.util.*;
import org.hibernate.*;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.*;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.util.Properties;

public final class SnowflakeGenerator implements IdentifierGenerator, Configurable{
    public static final long INSIDE_BOT_EPOCH = 1598384634000L;

    private SnowflakeIdGenerator idGenerator;

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws MappingException{
        long epoch = Strings.parseLong(params.getProperty("epoch"), INSIDE_BOT_EPOCH);
        long workerId = Strings.parseLong(params.getProperty("workerId"), 0);
        long processId = Strings.parseLong(params.getProperty("processId"), 0);

        idGenerator = new SnowflakeIdGenerator(epoch, workerId, processId);
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object obj) throws HibernateException{
        return idGenerator.nextId();
    }
}
