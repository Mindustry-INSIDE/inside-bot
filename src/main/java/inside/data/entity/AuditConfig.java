package inside.data.entity;

import discord4j.common.util.Snowflake;
import inside.audit.AuditActionType;
import inside.data.entity.base.ConfigEntity;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serial;
import java.util.*;

@Entity
@Table(name = "audit_config")
public class AuditConfig extends ConfigEntity{
    @Serial
    private static final long serialVersionUID = 31286754508621209L;

    @Column(name = "log_channel_id")
    private String logChannelId;

    @Type(type = "json")
    @Column(columnDefinition = "json")
    private Set<AuditActionType> types;

    public Optional<Snowflake> logChannelId(){
        return Optional.ofNullable(logChannelId).map(Snowflake::of);
    }

    public void logChannelId(Snowflake logChannelId){
        this.logChannelId = Objects.requireNonNull(logChannelId, "logChannelId").asString();
    }

    public Set<AuditActionType> types(){
        if(types == null){
            types = new HashSet<>();
        }
        return types;
    }

    public void types(Set<AuditActionType> types){
        this.types = Objects.requireNonNull(types, "types");
    }

    @Transient
    public boolean isEnabled(AuditActionType type){
        return logChannelId != null && types != null &&
                enabled && !types.isEmpty() && types.contains(type);
    }

    @Override
    public String toString(){
        return "AuditConfig{" +
                "logChannelId='" + logChannelId + '\'' +
                ", types=" + types +
                "} " + super.toString();
    }
}
