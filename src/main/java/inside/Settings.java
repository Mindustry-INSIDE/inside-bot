package inside;

import discord4j.rest.util.Color;
import org.joda.time.DateTimeZone;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.*;

@ConfigurationProperties("insidebot")
public class Settings{

    private String token;

    private final Discord discord = new Discord();

    private final Defaults defaults = new Defaults();

    private final Audit audit = new Audit();

    public String getToken(){
        return token;
    }

    public void setToken(String token){
        this.token = token;
    }

    public Discord getDiscord(){
        return discord;
    }

    public Defaults getDefaults(){
        return defaults;
    }

    public Audit getAudit(){
        return audit;
    }

    public static class Discord{

        private int maxClearedCount = 100;

        private boolean encryptMessages = true;

        private boolean auditLogSaving = false;

        private Duration errorEmbedTtl = Duration.ofSeconds(7);

        private String youtubeApiKey;

        // TODO: implement api
        private String spotifyApiKey;

        public int getMaxClearedCount(){
            return maxClearedCount;
        }

        public void setMaxClearedCount(int maxClearedCount){
            this.maxClearedCount = maxClearedCount;
        }

        public boolean isEncryptMessages(){
            return encryptMessages;
        }

        public void setEncryptMessages(boolean encryptMessages){
            this.encryptMessages = encryptMessages;
        }

        public boolean isAuditLogSaving(){
            return auditLogSaving;
        }

        public void setAuditLogSaving(boolean auditLogSaving){
            this.auditLogSaving = auditLogSaving;
        }

        public Duration getErrorEmbedTtl(){
            return errorEmbedTtl;
        }

        public void setErrorEmbedTtl(Duration errorEmbedTtl){
            this.errorEmbedTtl = errorEmbedTtl;
        }

        public String getYoutubeApiKey(){
            return youtubeApiKey;
        }

        public void setYoutubeApiKey(String youtubeApiKey){
            this.youtubeApiKey = youtubeApiKey;
        }

        public String getSpotifyApiKey(){
            return spotifyApiKey;
        }

        public void setSpotifyApiKey(String spotifyApiKey){
            this.spotifyApiKey = spotifyApiKey;
        }
    }

    public static class Defaults{

        private List<String> prefixes = Arrays.asList("#");

        private DateTimeZone timeZone = DateTimeZone.forID("Etc/Greenwich");

        private Color normalColor = Color.of(0xc4f5b7);

        private Color errorColor = Color.of(0xff3838);

        private int maxWarnings = 3;

        private Duration warnExpire = Duration.ofDays(21);

        private Duration muteEvade = Duration.ofDays(15);

        private int starboardLowerStarBarrier = 3;

        private Duration activeUserKeepCountingPeriod = Duration.ofDays(21);

        private int activeUserMessageBarrier = 75;

        public List<String> getPrefixes(){
            return prefixes;
        }

        public void setPrefix(List<String> prefixes){
            this.prefixes = prefixes;
        }

        public DateTimeZone getTimeZone(){
            return timeZone;
        }

        public void setTimeZone(String timeZone){
            this.timeZone = DateTimeZone.forID(timeZone);
        }

        public Color getNormalColor(){
            return normalColor;
        }

        public void setNormalColor(int normalColor){
            this.normalColor = Color.of(normalColor);
        }

        public Color getErrorColor(){
            return errorColor;
        }

        public void setErrorColor(int errorColor){
            this.errorColor = Color.of(errorColor);
        }

        public int getMaxWarnings(){
            return maxWarnings;
        }

        public void setMaxWarnings(int maxWarnings){
            this.maxWarnings = maxWarnings;
        }

        public Duration getWarnExpire(){
            return warnExpire;
        }

        public void setWarnExpire(Duration warnExpire){
            this.warnExpire = warnExpire;
        }

        public Duration getMuteEvade(){
            return muteEvade;
        }

        public void setMuteEvade(Duration muteEvade){
            this.muteEvade = muteEvade;
        }

        public Duration getActiveUserKeepCountingPeriod(){
            return activeUserKeepCountingPeriod;
        }

        public void setActiveUserKeepCountingPeriod(Duration activeUserKeepCountingPeriod){
            this.activeUserKeepCountingPeriod = activeUserKeepCountingPeriod;
        }

        public int getActiveUserMessageBarrier(){
            return activeUserMessageBarrier;
        }

        public void setActiveUserMessageBarrier(int activeUserMessageBarrier){
            this.activeUserMessageBarrier = activeUserMessageBarrier;
        }

        public int getStarboardLowerStarBarrier(){
            return starboardLowerStarBarrier;
        }

        public void setStarboardLowerStarBarrier(int starboardLowerStarBarrier){
            this.starboardLowerStarBarrier = starboardLowerStarBarrier;
        }
    }

    public static class Audit{

        private Duration historyKeep = Duration.ofDays(21);

        private Duration memberKeep = Duration.ofDays(160);

        private Duration starboardKeep = Duration.ofDays(14);

        public Duration getHistoryKeep(){
            return historyKeep;
        }

        public void setHistoryKeep(Duration historyKeep){
            this.historyKeep = historyKeep;
        }

        public Duration getMemberKeep(){
            return memberKeep;
        }

        public void setMemberKeep(Duration memberKeep){
            this.memberKeep = memberKeep;
        }

        public Duration getStarboardKeep(){
            return starboardKeep;
        }

        public void setStarboardKeep(Duration starboardKeep){
            this.starboardKeep = starboardKeep;
        }
    }
}
