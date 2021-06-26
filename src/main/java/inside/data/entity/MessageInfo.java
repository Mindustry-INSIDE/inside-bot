package inside.data.entity;

import discord4j.common.util.Snowflake;
import inside.data.entity.base.GuildEntity;

import javax.persistence.*;
import java.io.Serial;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "message_info")
public class MessageInfo extends GuildEntity{
    @Serial
    private static final long serialVersionUID = -7977287922184407665L;

    @Column(name = "message_id")
    private long messageId;

    @Column(name = "user_id")
    private long userId;

    @Column(columnDefinition = "text")
    private String content;

    @Column
    private Instant timestamp;

    public Snowflake messageId(){
        return Snowflake.of(messageId);
    }

    public void messageId(Snowflake messageId){
        this.messageId = Objects.requireNonNull(messageId, "messageId").asLong();
    }

    public Snowflake userId(){
        return Snowflake.of(userId);
    }

    public void userId(Snowflake userId){
        this.userId = Objects.requireNonNull(userId, "userId").asLong();
    }

    public String content(){
        return content;
    }

    public void content(String content){
        this.content = Objects.requireNonNull(content, "content");
    }

    public Instant timestamp(){
        return timestamp;
    }

    public void timestamp(Instant timestamp){
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    @Override
    public String toString(){
        return "MessageInfo{" +
                "messageId=" + messageId +
                ", userId=" + userId +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                "} " + super.toString();
    }
}
