package inside.scheduler.job;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.rest.util.AllowedMentions;
import inside.service.DiscordService;
import inside.util.DiscordUtil;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.util.*;

import java.util.UUID;

public class RemindJob implements Job{
    private static final Logger log = Loggers.getLogger(RemindJob.class);

    private static final String GROUP = "RemindJob-group";

    protected static final String ATT_USER_ID = "user_id";
    protected static final String ATT_GUILD_ID = "guild_id";
    private static final String ATT_CHANNEL_ID = "channel_id";
    private static final String ATT_MESSAGE = "message";

    @Autowired
    private DiscordService discordService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException{
        Snowflake guildId = Snowflake.of(context.getMergedJobDataMap().getString(ATT_GUILD_ID));
        Snowflake userId = Snowflake.of(context.getMergedJobDataMap().getString(ATT_USER_ID));
        Snowflake channelId = Snowflake.of(context.getMergedJobDataMap().getString(ATT_CHANNEL_ID));
        String text = context.getMergedJobDataMap().getString(ATT_MESSAGE);

        discordService.gateway().getGuildById(guildId)
                .flatMap(guild -> guild.getChannelById(channelId))
                .ofType(GuildMessageChannel.class)
                .flatMap(channel -> channel.createMessage(spec -> spec.setAllowedMentions(AllowedMentions.suppressEveryone())
                        .setContent(String.format("%s, %s", DiscordUtil.getUserMention(userId), text))))
                .subscribe();
    }

    public static JobDetail createDetails(Snowflake guildId, Snowflake userId, Snowflake channelId, String message){
        return JobBuilder.newJob(RemindJob.class)
                .withIdentity(GROUP + "-" + UUID.randomUUID(), GROUP)
                .usingJobData(ATT_GUILD_ID, guildId.asString())
                .usingJobData(ATT_USER_ID, userId.asString())
                .usingJobData(ATT_CHANNEL_ID, channelId.asString())
                .usingJobData(ATT_MESSAGE, message)
                .build();
    }
}
