package inside.audit;

import discord4j.common.util.Snowflake;
import inside.data.entity.AuditAction;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.io.InputStream;
import java.util.List;

public interface AuditService{

    Mono<Void> save(AuditAction action, List<Tuple2<String, InputStream>> attachments);

    AuditActionBuilder newBuilder(Snowflake guildId, AuditActionType type);

    void cleanUp();
}
