package inside.service;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import inside.data.entity.*;
import reactor.core.publisher.*;
import reactor.util.annotation.Nullable;

import java.time.Instant;

// action service
public interface AdminService{

    Flux<AdminAction> get(AdminActionType type, Snowflake guildId, Snowflake targetId);

    Flux<AdminAction> getAll(AdminActionType type);

    Mono<Void> mute(Member admin, Member target, Instant end, @Nullable String reason);

    Mono<Boolean> isMuted(Snowflake guildId, Snowflake targetId);

    default Mono<Boolean> isMuted(Member member){
        return isMuted(member.getGuildId(), member.getId());
    }

    Mono<Void> unmute(Member target);

    Mono<Void> warn(Member admin, Member target, @Nullable String reason);

    Mono<Void> unwarn(Snowflake guildId, Snowflake targetId, int index);

    default Mono<Void> unwarn(Member target, int index){
        return unwarn(target.getGuildId(), target.getId(), index);
    }

    Flux<AdminAction> warnings(Snowflake guildId, Snowflake targetId);

    default Flux<AdminAction> warnings(Member member){
        return warnings(member.getGuildId(), member.getId());
    }

    Mono<Boolean> isOwner(Member member);

    Mono<Boolean> isAdmin(Member member);
}
