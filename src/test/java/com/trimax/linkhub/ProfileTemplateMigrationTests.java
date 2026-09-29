package com.trimax.linkhub;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ProfileTemplateMigrationTests {
    @Test void upgradingExistingAccountDefaultsToClassicAndRejectsUnknownTemplates() throws Exception {
        String url = "jdbc:h2:mem:template_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        // Keep the H2 session that compiled IN constraints alive for the full test.
        var dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        try {
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into accounts (id,email,password_hash,username,display_name,bio,published,created_at) "
                    + "values ('00000000-0000-0000-0000-000000000001','existing@example.com','hash','existing','Existing profile','Keep this bio',true,current_timestamp)");
            Flyway.configure().dataSource(dataSource).load().migrate();
            try (var row = statement.executeQuery("select profile_template, bio, published from accounts where username='existing'")) {
                assertTrue(row.next());
                assertEquals("CLASSIC", row.getString("profile_template"));
                assertEquals("Keep this bio", row.getString("bio"));
                assertTrue(row.getBoolean("published"));
            }
            SQLException invalid = assertThrows(SQLException.class, () -> statement.executeUpdate("update accounts set profile_template='UNKNOWN'"));
            assertTrue(invalid.getSQLState().startsWith("23"));
            statement.executeUpdate("update accounts set profile_template='BUSINESS'");
            try (var row = statement.executeQuery("select count(*) from landing_blocks")) {
                assertTrue(row.next());
                assertEquals(0, row.getInt(1));
            }
        }
        } finally { dataSource.destroy(); }
    }
}
