package inside.scheduler.job;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import inside.data.service.*;
import inside.service.DiscordService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static inside.scheduler.job.RemindJob.*;
import static inside.util.ContextUtil.*;

public class UnmuteJob implements Job{
    private static final String GROUP = "UnmuteJob-group";

    @Autowired
    private DiscordService discordService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private EntityRetriever entityRetriever;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException{
        Snowflake guildId = Snowflake.of(context.getMergedJobDataMap().getString(ATT_GUILD_ID));
        Snowflake userId = Snowflake.of(context.getMergedJobDataMap().getString(ATT_USER_ID));

        discordService.gateway().getGuildById(guildId)
                .flatMap(guild -> guild.getMemberById(userId))
                .flatMap(target -> entityRetriever.getGuildConfigById(target.getGuildId()).flatMap(guildConfig ->
                        adminService.unmute(target).contextWrite(ctx -> ctx.put(KEY_LOCALE, guildConfig.locale())
                                .put(KEY_TIMEZONE, guildConfig.timeZone()))))
                .subscribe();
    }

    public static JobDetail createDetails(Member member){
        return JobBuilder.newJob(UnmuteJob.class)
                .withIdentity(GROUP + "-" + UUID.randomUUID(), GROUP)
                .usingJobData(ATT_GUILD_ID, member.getGuildId().asString())
                .usingJobData(ATT_USER_ID, member.getId().asString())
                .build();
    }
}
