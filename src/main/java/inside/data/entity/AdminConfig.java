package inside.data.entity;

import discord4j.common.util.Snowflake;
import discord4j.discordjson.Id;
import inside.data.entity.base.GuildEntity;
import org.hibernate.annotations.Type;
import reactor.util.annotation.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "admin_config")
public class AdminConfig extends GuildEntity{
    @Serial
    private static final long serialVersionUID = 3870326130058466675L;

    @Column(name = "warn_expire_delay")
    private Duration warnExpireDelay;

    @Column(name = "mute_base_delay")
    private Duration muteBaseDelay;

    @Column(name = "max_warn_count")
    private long maxWarnCount;

    @Column(name = "threshold_action")
    @Enumerated(EnumType.STRING)
    private AdminActionType thresholdAction;

    @Column(name = "mute_role_id")
    private String muteRoleId;

    @Type(type = "json")
    @Column(name = "admin_role_ids", columnDefinition = "json")
    private Set<Id> adminRoleIds;

    @Nullable
    public Duration warnExpireDelay(){
        return warnExpireDelay;
    }

    public void warnExpireDelay(@Nullable Duration warnExpireDelay){
        this.warnExpireDelay = warnExpireDelay;
    }

    public Duration muteBaseDelay(){
        return muteBaseDelay;
    }

    public void muteBaseDelay(Duration muteBaseDelay){
        this.muteBaseDelay = muteBaseDelay;
    }

    public long maxWarnCount(){
        return maxWarnCount;
    }

    public void maxWarnCount(long maxWarnCount){
        this.maxWarnCount = maxWarnCount;
    }

    public Optional<Snowflake> muteRoleID(){
        return Optional.ofNullable(muteRoleId).map(Snowflake::of);
    }

    public void muteRoleId(Snowflake muteRoleId){
        this.muteRoleId = Objects.requireNonNull(muteRoleId, "muteRoleId").asString();
    }

    public Set<Snowflake> adminRoleIds(){
        if(adminRoleIds == null){
            adminRoleIds = new HashSet<>();
        }
        return adminRoleIds.stream()
                .map(Snowflake::of)
                .collect(Collectors.toSet());
    }

    public void adminRoleIds(Set<Snowflake> adminRoleIDs){
        Objects.requireNonNull(adminRoleIDs, "adminRoleIDs");
        this.adminRoleIds = adminRoleIDs.stream()
                .map(id -> Id.of(id.asLong()))
                .collect(Collectors.toSet());
    }

    public AdminActionType thresholdAction(){
        return thresholdAction;
    }

    public void thresholdAction(AdminActionType thresholdAction){
        this.thresholdAction = Objects.requireNonNull(thresholdAction, "thresholdAction");
    }

    @Override
    public String toString(){
        return "AdminConfig{" +
                "warnExpireDelay=" + warnExpireDelay +
                ", muteBaseDelay=" + muteBaseDelay +
                ", maxWarnCount=" + maxWarnCount +
                ", thresholdAction=" + thresholdAction +
                ", muteRoleId='" + muteRoleId + '\'' +
                ", adminRoleIds=" + adminRoleIds +
                "} " + super.toString();
    }
}
