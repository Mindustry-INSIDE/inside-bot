package inside.data.entity;

import discord4j.common.util.Snowflake;
import inside.data.entity.base.ConfigEntity;

import javax.persistence.*;
import java.io.Serial;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "active_user_config")
public class ActiveUserConfig extends ConfigEntity{
    @Serial
    private static final long serialVersionUID = -3848703477201041407L;

    @Column(name = "keep_counting_duration")
    private Duration keepCountingDuration;

    @Column(name = "message_barrier")
    private int messageBarrier;

    @Column(name = "role_id")
    private String roleId;

    @Transient
    public boolean resetIfAfter(Activity activity){
        Objects.requireNonNull(activity, "activity");
        Instant last = activity.lastSentMessage();
        if(last != null && last.isBefore(Instant.now().minus(keepCountingDuration))){
            activity.messageCount(0);
            return true;
        }
        return false;
    }

    @Transient
    public boolean isActive(Activity activity){
        Objects.requireNonNull(activity, "activity");
        Instant last = activity.lastSentMessage();
        return last != null && last.isAfter(Instant.now().minus(keepCountingDuration)) &&
                activity.messageCount() >= messageBarrier;
    }

    public Duration keepCountingDuration(){
        return keepCountingDuration;
    }

    public void keepCountingDuration(Duration keepCountingDuration){
        this.keepCountingDuration = Objects.requireNonNull(keepCountingDuration, "keepCountingDuration");
    }

    public int messageBarrier(){
        return messageBarrier;
    }

    public void messageBarrier(int messageBarrier){
        this.messageBarrier = messageBarrier;
    }

    public Optional<Snowflake> roleId(){
        return Optional.ofNullable(roleId).map(Snowflake::of);
    }

    public void roleId(Snowflake roleId){
        this.roleId = Objects.requireNonNull(roleId, "roleId").asString();
    }

    @Override
    public String toString(){
        return "ActiveUserConfig{" +
                "keepCountingDuration=" + keepCountingDuration +
                ", messageBarrier=" + messageBarrier +
                ", roleId='" + roleId + '\'' +
                "} " + super.toString();
    }
}
