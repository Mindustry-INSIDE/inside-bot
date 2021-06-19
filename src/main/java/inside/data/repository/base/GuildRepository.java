package inside.data.repository.base;

import inside.data.entity.base.GuildEntity;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface GuildRepository<T extends GuildEntity> extends BaseRepository<T>{

    T findByGuildId(long id);

    void deleteByGuildId(long id);

    void deleteAllByGuildId(long id);
}
