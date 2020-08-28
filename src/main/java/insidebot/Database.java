package insidebot;

import arc.util.Log;
import org.h2.tools.Server;

import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import static insidebot.InsideBot.config;

public class Database {
    private Connection con;

    public Database(){
        try {
            Class.forName("org.h2.Driver");
            Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
            con = DriverManager.getConnection(config.get("db-url"), config.get("db-username"), config.get("db-password"));
            Log.info("The database connection is made.");
            init();
        }catch (SQLException | ClassNotFoundException e){
            Log.err(e);
        }
    }

    public void init(){
        try {
            getCon().createStatement().execute(
                "CREATE SCHEMA IF NOT EXISTS DISCORD;"
            );
            getCon().createStatement().execute(
                "CREATE TABLE IF NOT EXISTS DISCORD.USERS_INFO (NAME VARCHAR(40), ID LONG, LAST_SENT_MESSAGE_DATE VARCHAR(20), LAST_SENT_MESSAGE_ID LONG, MESSAGES_PER_WEEK INT(11), WARNS INT(11), MUTE_END_DATE VARCHAR(20));"
            );
        } catch (SQLException e) {
            Log.info(e);
        }
    }

    public Connection getCon() {
        return con;
    }

    public UserInfo getUserInfo(long id){
        try {
            PreparedStatement statement = getCon().prepareStatement("SELECT * FROM DISCORD.USERS_INFO WHERE ID=?;");

            statement.setLong(1, id);

            ResultSet resultSet = statement.executeQuery();

            String name = "";
            long lastMessageId = 0L;
            while (resultSet.next()) {
                name = resultSet.getString("NAME");
                lastMessageId = resultSet.getLong("LAST_SENT_MESSAGE_ID");
            }

            return new UserInfo(name, id, lastMessageId);
        } catch (SQLException e) {
            Log.err(e);
            return null;
        }
    }

    public DateFormat format(){
        return new SimpleDateFormat("MM-dd HH:mm");
    }
}
