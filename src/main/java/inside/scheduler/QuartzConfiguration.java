package inside.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;

@Configuration
public class QuartzConfiguration implements SchedulerFactoryBeanCustomizer{

    @Autowired
    private DataSource dataSource;

    @Bean
    public SpringBeanJobFactory jobFactory(){
        return new SpringBeanJobFactory();
    }

    @Override
    public void customize(SchedulerFactoryBean schedulerFactoryBean){
        schedulerFactoryBean.setJobFactory(jobFactory());
        schedulerFactoryBean.setDataSource(dataSource);
    }
}
