package inside.data.repository;

import inside.data.entity.LocalMember;
import inside.data.repository.base.GuildRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface LocalMemberRepository extends GuildRepository<LocalMember>{

    LocalMember findByUserIdAndGuildId(long userId, long guildId);

    void deleteAllByActivityLastSentMessageBefore(Instant timestamp);
}
