package inside.data.repository;

import inside.data.entity.EmojiDispenser;
import inside.data.repository.base.GuildRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmojiDispenserRepository extends GuildRepository<EmojiDispenser>{

    EmojiDispenser findByMessageIdAndRoleId(long messageId, long roleId);

    List<EmojiDispenser> findAllByMessageId(long messageId);

    List<EmojiDispenser> getAllByGuildId(long guildId);
}
