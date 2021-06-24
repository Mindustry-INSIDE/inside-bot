package inside.data.entity;

import discord4j.common.util.Snowflake;
import inside.data.entity.base.ConfigEntity;
import org.joda.time.*;

import javax.persistence.*;
import java.io.Serial;
import java.util.*;

@Entity
@Table(name = "active_user_config")
public class ActiveUserConfig extends ConfigEntity{
    @Serial
    private static final long serialVersionUID = -3848703477201041407L;

    @Column(name = "keep_counting_period")
    private Duration keepCountingPeriod;

    @Column(name = "message_barrier")
    private int messageBarrier;

    @Column(name = "role_id")
    private String roleId;

    @Transient
    public boolean resetIfAfter(Activity activity){
        Objects.requireNonNull(activity, "activity");
        DateTime last = activity.lastSentMessage();
        if(last != null && last.isBefore(DateTime.now().minus(keepCountingPeriod))){
            activity.messageCount(0);
            return true;
        }
        return false;
    }

    @Transient
    public boolean isActive(Activity activity){
        Objects.requireNonNull(activity, "activity");
        DateTime last = activity.lastSentMessage();
        return last != null && last.isAfter(DateTime.now().minus(keepCountingPeriod)) &&
                activity.messageCount() >= messageBarrier;
    }

    public Duration keepCountingPeriod(){
        return keepCountingPeriod;
    }

    public void keepCountingPeriod(Duration keepCountingPeriod){
        this.keepCountingPeriod = Objects.requireNonNull(keepCountingPeriod, "keepCountingPeriod");
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
                "keepCountingPeriod=" + keepCountingPeriod +
                ", messageBarrier=" + messageBarrier +
                ", roleId='" + roleId + '\'' +
                "} " + super.toString();
    }
}
