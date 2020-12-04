package insidebot.common.services;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import insidebot.event.dispatcher.EventListener;
import reactor.core.publisher.Mono;

public interface DiscordService{

    GatewayDiscordClient gateway();

    EventListener eventListener();

    Mono<PrivateChannel> getPrivateChannelById(Snowflake userId);

    Mono<TextChannel> getTextChannelById(Snowflake channelId);

    Mono<TextChannel> getLogChannel(Snowflake guildId);

    Mono<VoiceChannel> getVoiceChannelById(Snowflake channelId);

    boolean exists(Snowflake guildId, Snowflake userId);
}
