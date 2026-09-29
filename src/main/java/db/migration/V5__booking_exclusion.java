package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** H2 only exercises portable API tests; exclusion is tested against real PostgreSQL. */
public class V5__booking_exclusion extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        String vendor = context.getConnection().getMetaData().getDatabaseProductName();
        if (vendor.equals("H2")) return;
        if (!vendor.equals("PostgreSQL")) throw new IllegalStateException("Booking requires PostgreSQL");
        try (var sql = context.getConnection().createStatement()) {
            sql.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
            sql.execute("""
                ALTER TABLE bookings ADD CONSTRAINT bookings_no_overlap
                EXCLUDE USING gist (owner_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
                WHERE (status IN ('PENDING', 'CONFIRMED'))
                """);
        }
    }
}
