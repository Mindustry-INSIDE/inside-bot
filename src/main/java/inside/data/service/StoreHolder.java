package inside.data.service;

import inside.data.service.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class StoreHolder{
    private final GuildConfigService guildConfigService;

    private final AdminConfigService adminConfigService;

    private final LocalMemberService localMemberService;

    private final AuditConfigService auditConfigService;

    private final MessageInfoService messageInfoService;

    private final StarboardConfigService starboardConfigService;

    private final StarboardService starboardService;

    private final ActivityConfigService activityConfig;

    private final EmojiDispenserService emojiDispenserService;

    public StoreHolder(@Autowired GuildConfigService guildConfigService,
                       @Autowired AdminConfigService adminConfigService,
                       @Autowired LocalMemberService localMemberService,
                       @Autowired AuditConfigService auditConfigService,
                       @Autowired MessageInfoService messageInfoService,
                       @Autowired StarboardConfigService starboardConfigService,
                       @Autowired StarboardService starboardService,
                       @Autowired ActivityConfigService activityConfig,
                       @Autowired EmojiDispenserService emojiDispenserService){
        this.guildConfigService = guildConfigService;
        this.adminConfigService = adminConfigService;
        this.localMemberService = localMemberService;
        this.auditConfigService = auditConfigService;
        this.messageInfoService = messageInfoService;
        this.starboardConfigService = starboardConfigService;
        this.starboardService = starboardService;
        this.activityConfig = activityConfig;
        this.emojiDispenserService = emojiDispenserService;
    }

    public GuildConfigService getGuildConfigService(){
        return guildConfigService;
    }

    public AdminConfigService getAdminConfigService(){
        return adminConfigService;
    }

    public LocalMemberService getLocalMemberService(){
        return localMemberService;
    }

    public AuditConfigService getAuditConfigService(){
        return auditConfigService;
    }

    public MessageInfoService getMessageInfoService(){
        return messageInfoService;
    }

    public StarboardConfigService getStarboardConfigService(){
        return starboardConfigService;
    }

    public StarboardService getStarboardService(){
        return starboardService;
    }

    public ActivityConfigService getActivityConfigService(){
        return activityConfig;
    }

    public EmojiDispenserService getEmojiDispenserService(){
        return emojiDispenserService;
    }
}
