package inside.data.service;

import inside.Settings;
import inside.data.entity.base.BaseEntity;
import inside.data.repository.base.BaseRepository;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public abstract class BaseLongObjEntityService<V extends BaseEntity, R extends BaseRepository<V>>
        extends BaseEntityService<Long, V, R>
        implements LongObjEntityService<V>{

    protected BaseLongObjEntityService(R repository, Settings settings){
        super(repository, settings);
    }

    @Override
    public Mono<V> find(long id){
        return Mono.defer(() -> Mono.justOrEmpty(find0(id)));
    }

    @Nullable
    @Override
    protected V find0(Long id){
        return find0((long)id);
    }

    @Nullable
    protected abstract V find0(long id);

    @Override
    public Mono<Void> delete(long id){
        return find(id).flatMap(this::delete);
    }
}
