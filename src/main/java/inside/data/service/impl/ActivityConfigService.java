package inside.data.service.impl;

import inside.Settings;
import inside.data.entity.ActivityConfig;
import inside.data.repository.ActivityConfigRepository;
import inside.data.service.BaseLongObjEntityService;
import org.springframework.stereotype.Service;
import reactor.util.annotation.Nullable;

@Service
public class ActivityConfigService extends BaseLongObjEntityService<ActivityConfig, ActivityConfigRepository>{

    protected ActivityConfigService(ActivityConfigRepository repository, Settings settings){
        super(repository, settings);
    }

    @Nullable
    @Override
    protected ActivityConfig find0(long id){
        return repository.findByGuildId(id);
    }
}
