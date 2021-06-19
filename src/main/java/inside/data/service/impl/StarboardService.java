package inside.data.service.impl;

import discord4j.store.api.util.LongLongTuple2;
import inside.Settings;
import inside.data.entity.Starboard;
import inside.data.repository.StarboardRepository;
import inside.data.service.BaseEntityService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@Service
public class StarboardService extends BaseEntityService<LongLongTuple2, Starboard, StarboardRepository>{

    protected StarboardService(StarboardRepository repository, Settings settings){
        super(repository, settings);
    }

    @Nullable
    @Override
    protected Starboard find0(LongLongTuple2 id){
        long guildId = id.getT1();
        long sourceMessageId = id.getT2();
        return repository.findByGuildIdAndSourceMessageId(guildId, sourceMessageId);
    }

    public Mono<Void> deleteAllByGuildId(long guildId){
        return Mono.fromRunnable(() -> repository.deleteAllByGuildId(guildId));
    }
}
