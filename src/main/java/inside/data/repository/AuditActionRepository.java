package inside.data.repository;

import inside.data.entity.AuditAction;
import inside.data.repository.base.GuildRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface AuditActionRepository extends GuildRepository<AuditAction>{

    @Transactional // why spring?
    void deleteAllByTimestampBefore(Instant timestamp);
}
