package inside.data.service.impl;

import inside.Settings;
import inside.data.entity.MessageInfo;
import inside.data.repository.MessageInfoRepository;
import inside.data.service.BaseLongObjEntityService;
import org.joda.time.DateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

// or LongLongTuple2?
@Service
public class MessageInfoService extends BaseLongObjEntityService<MessageInfo, MessageInfoRepository>{

    protected MessageInfoService(MessageInfoRepository repository, Settings settings){
        super(repository, settings);
    }

    @Nullable
    @Override
    protected MessageInfo find0(long id){
        return repository.findByMessageId(id);
    }

    public Mono<Void> deleteAllByGuildId(long guildId){
        return Mono.fromRunnable(() -> repository.deleteAllByGuildId(guildId));
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 */4 * * *")
    public void cleanUp(){
        repository.deleteAllByTimestampBefore(DateTime.now().minus(settings.getAudit().getHistoryKeep().toMillis()));
    }
}
