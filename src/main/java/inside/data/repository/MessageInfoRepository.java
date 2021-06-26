package inside.data.repository;

import inside.data.entity.MessageInfo;
import inside.data.repository.base.GuildRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface MessageInfoRepository extends GuildRepository<MessageInfo>{

    MessageInfo findByMessageId(long messageId);

    void deleteByMessageId(long messageId);

    void deleteAllByTimestampBefore(LocalDateTime timestamp);
}
