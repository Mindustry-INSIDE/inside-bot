package inside.data.service;

import inside.Settings;
import inside.data.entity.base.BaseEntity;
import inside.data.repository.base.BaseRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.*;
import reactor.util.annotation.Nullable;

public abstract class BaseEntityService<K, V extends BaseEntity, R extends BaseRepository<V>> implements EntityService<K, V>{

    protected final R repository;

    protected final Settings settings;

    protected BaseEntityService(R repository, Settings settings){
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    public Mono<V> find(K id){
        return Mono.defer(() -> Mono.justOrEmpty(find0(id)));
    }

    @Override
    @Transactional
    public Flux<V> getAll(){
        return Flux.defer(() -> Flux.fromIterable(repository.findAll()));
    }

    @Nullable
    protected abstract V find0(K id);

    @Override
    @Transactional
    public Mono<Void> save(V entity){
        return Mono.fromRunnable(() -> repository.save(entity));
    }

    @Override
    @Transactional
    public Mono<Void> delete(K id){
        return find(id).flatMap(this::delete);
    }

    @Override
    @Transactional
    public Mono<Void> delete(V entity){
        return Mono.fromRunnable(() -> repository.delete(entity));
    }

    protected void cleanUp(){
        // no-op
    }
}
