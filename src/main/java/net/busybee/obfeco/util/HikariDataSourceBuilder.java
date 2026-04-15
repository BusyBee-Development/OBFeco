package net.busybee.obfeco.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;

public class HikariDataSourceBuilder {
    public static HikariDataSource createSqlite(File dbFile) {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setPoolName("CoinsEngine-SQLite");

        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);

        return new HikariDataSource(config);
    }

    public static HikariDataSource createMySql(String host, int port,
                                               String database,
                                               String user, String pass) {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&characterEncoding=UTF-8");

        config.setUsername(user);
        config.setPassword(pass);

        config.setPoolName("CoinsEngine-MySQL");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        return new HikariDataSource(config);
    }
}
