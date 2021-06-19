package inside.data.service.impl;

import inside.Settings;
import inside.data.entity.EmojiDispenser;
import inside.data.repository.EmojiDispenserRepository;
import inside.data.service.BaseLongObjEntityService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@Service
public class EmojiDispenserService extends BaseLongObjEntityService<EmojiDispenser, EmojiDispenserRepository>{

    protected EmojiDispenserService(EmojiDispenserRepository repository, Settings settings){
        super(repository, settings);
    }

    @Nullable
    @Override
    protected EmojiDispenser find0(long id){
        return repository.findByMessageId(id);
    }

    public Mono<Void> deleteAllByGuildId(long guildId){
        return Mono.fromRunnable(() -> repository.deleteAllByGuildId(guildId));
    }
}
