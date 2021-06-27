package inside.data.repository;

import inside.audit.AuditActionType;
import inside.data.entity.AuditAction;
import inside.data.repository.base.GuildRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditActionRepository extends GuildRepository<AuditAction>{

    @Transactional // why spring?
    void deleteAllByTimestampBefore(Instant timestamp);

    List<AuditAction> findAllByTypeAndGuildId(AuditActionType type, long guildId);
}
