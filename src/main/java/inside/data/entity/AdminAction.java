package inside.data.entity;

import inside.data.entity.base.GuildEntity;
import org.hibernate.annotations.*;
import org.immutables.builder.Builder;
import org.joda.time.DateTime;
import reactor.util.annotation.Nullable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serial;
import java.util.Optional;

@Entity
@Table(name = "admin_action")
public class AdminAction extends GuildEntity{
    @Serial
    private static final long serialVersionUID = 5778834003599760075L;

    @Enumerated(EnumType.STRING)
    private AdminActionType type;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "admin_id")
    private LocalMember admin;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "target_id")
    private LocalMember target;

    @Column(length = 512)
    private String reason;

    @Column
    private DateTime timestamp;

    @Column(name = "end_timestamp")
    private DateTime endTimestamp;

    protected AdminAction(){}

    @Builder.Constructor
    protected AdminAction(long guildId, AdminActionType type,
                          LocalMember admin, LocalMember target,
                          @Nullable String reason, DateTime timestamp,
                          @Nullable DateTime endTimestamp){
        this.guildId = guildId;
        this.type = type;
        this.admin = admin;
        this.target = target;
        this.reason = reason;
        this.timestamp = timestamp;
        this.endTimestamp = endTimestamp;
    }

    public static AdminActionBuilder builder(){
        return new AdminActionBuilder();
    }

    public AdminActionType type(){
        return type;
    }

    public LocalMember admin(){
        return admin;
    }

    public LocalMember target(){
        return target;
    }

    public Optional<String> reason(){
        return Optional.ofNullable(reason);
    }

    public DateTime timestamp(){
        return timestamp;
    }

    public Optional<DateTime> endTimestamp(){
        return Optional.ofNullable(endTimestamp);
    }

    @Override
    public String toString(){
        return "AdminAction{" +
                "type=" + type +
                ", admin=" + admin +
                ", target=" + target +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                ", endTimestamp=" + endTimestamp +
                "} " + super.toString();
    }
}
