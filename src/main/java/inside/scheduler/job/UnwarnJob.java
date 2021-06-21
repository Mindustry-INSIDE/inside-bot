package inside.scheduler.job;

import discord4j.common.util.Snowflake;
import inside.data.entity.AdminAction;
import inside.data.repository.AdminActionRepository;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.UUID;

public class UnwarnJob implements Job{
    private static final String GROUP = "UnwarnJob-group";

    private static final String ATT_ID = "id";

    @Autowired
    private AdminActionRepository actionRepository;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException{
        long id = context.getMergedJobDataMap().getLongValue(ATT_ID);
        actionRepository.deleteById(id);
    }

    public static JobDetail createDetails(AdminAction action){
        return JobBuilder.newJob(UnwarnJob.class)
                .withIdentity(GROUP + "-" + UUID.randomUUID(), GROUP)
                .usingJobData(ATT_ID, Snowflake.asString(action.id()))
                .build();
    }
}
