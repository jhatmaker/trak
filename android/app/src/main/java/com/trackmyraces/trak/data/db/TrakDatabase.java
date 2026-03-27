package com.trackmyraces.trak.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.trackmyraces.trak.data.db.dao.CredentialEntryDao;
import com.trackmyraces.trak.data.db.dao.PendingMatchDao;
import com.trackmyraces.trak.data.db.dao.RaceResultDao;
import com.trackmyraces.trak.data.db.dao.UserSitePrefDao;
import com.trackmyraces.trak.data.db.dao.ResultClaimDao;
import com.trackmyraces.trak.data.db.dao.ResultSplitDao;
import com.trackmyraces.trak.data.db.dao.RunnerProfileDao;
import com.trackmyraces.trak.data.db.dao.SavedViewDao;
import com.trackmyraces.trak.data.db.entity.CredentialEntryEntity;
import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.data.db.entity.UserSitePrefEntity;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.db.entity.ResultClaimEntity;
import com.trackmyraces.trak.data.db.entity.ResultSplitEntity;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.db.entity.SavedViewEntity;

/**
 * TrakDatabase — Room database for the Trak app.
 *
 * Single instance per process (double-checked locking singleton).
 * All writes happen on background threads; Room enforces this.
 *
 * Version history:
 *   1 — initial schema (RunnerProfileEntity, RaceResultEntity)
 *   2 — added ResultSplitEntity, ResultClaimEntity, CredentialEntryEntity, SavedViewEntity
 *   3 — added elevation_gain_meters, temperature_celsius, weather_condition to race_result
 *   4 — added interests to runner_profile
 *   5 — added elevation_start_meters to race_result
 *   6 — added preferred_temp_unit to runner_profile
 *   7 — added pending_match table; added last_discover_at + pending_count to runner_profile
 *   8 — added user_site_pref table (per-user hide flag for default and custom sources)
 *   9 — added per-result detail columns to pending_match (race_name, race_date,
 *        distance_label, distance_meters, location, bib_number, finish_time,
 *        finish_seconds, overall_place, overall_total, raw_data)
 *  10 — added user_id to runner_profile (device-local UUID for backend identification)
 */
@Database(
    entities = {
        RunnerProfileEntity.class,
        RaceResultEntity.class,
        ResultSplitEntity.class,
        ResultClaimEntity.class,
        CredentialEntryEntity.class,
        SavedViewEntity.class,
        PendingMatchEntity.class,
        UserSitePrefEntity.class,
    },
    version = 10,
    exportSchema = true
)
public abstract class TrakDatabase extends RoomDatabase {

    private static final String DB_NAME = "trak.db";
    private static volatile TrakDatabase sInstance;

    // ── DAOs ──────────────────────────────────────────────────────────────

    public abstract RunnerProfileDao  runnerProfileDao();
    public abstract RaceResultDao     raceResultDao();
    public abstract ResultSplitDao    resultSplitDao();
    public abstract ResultClaimDao    resultClaimDao();
    public abstract CredentialEntryDao credentialEntryDao();
    public abstract SavedViewDao      savedViewDao();
    public abstract PendingMatchDao   pendingMatchDao();
    public abstract UserSitePrefDao   userSitePrefDao();

    // ── Singleton ─────────────────────────────────────────────────────────

    public static TrakDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (TrakDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TrakDatabase.class,
                            DB_NAME
                        )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                        .build();
                }
            }
        }
        return sInstance;
    }

    // ── Migrations ────────────────────────────────────────────────────────

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `result_split` (" +
                "`id` TEXT NOT NULL, " +
                "`result_id` TEXT, " +
                "`label` TEXT, " +
                "`distance_meters` REAL NOT NULL, " +
                "`elapsed_seconds` INTEGER NOT NULL, " +
                "`split_seconds` INTEGER, " +
                "`split_place` INTEGER, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`result_id`) REFERENCES `race_result`(`id`) ON DELETE CASCADE)"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_split_result_id` ON `result_split` (`result_id`)");

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `result_claim` (" +
                "`id` TEXT NOT NULL, " +
                "`race_event_id` TEXT, " +
                "`result_id` TEXT, " +
                "`status` TEXT, " +
                "`source_url` TEXT, " +
                "`is_manual` INTEGER NOT NULL, " +
                "`claimed_at` TEXT, " +
                "`updated_at` TEXT, " +
                "`is_synced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_claim_race_event_id` ON `result_claim` (`race_event_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_result_claim_result_id` ON `result_claim` (`result_id`)");

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `credential_entry` (" +
                "`id` TEXT NOT NULL, " +
                "`site_url` TEXT, " +
                "`site_label` TEXT, " +
                "`username` TEXT, " +
                "`keystore_alias` TEXT, " +
                "`login_status` TEXT, " +
                "`last_login_at` TEXT, " +
                "`cookie_expiry` TEXT, " +
                "`created_at` TEXT, " +
                "`updated_at` TEXT, " +
                "PRIMARY KEY(`id`))"
            );
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_credential_entry_site_url` ON `credential_entry` (`site_url`)");

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_view` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT, " +
                "`view_type` TEXT, " +
                "`distance` TEXT, " +
                "`surface` TEXT, " +
                "`year_from` INTEGER, " +
                "`year_to` INTEGER, " +
                "`race_name_slug` TEXT, " +
                "`sort` TEXT, " +
                "`order_dir` TEXT, " +
                "`created_at` TEXT, " +
                "`updated_at` TEXT, " +
                "`is_synced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
            );
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `race_result` ADD COLUMN `elevation_gain_meters` INTEGER");
            db.execSQL("ALTER TABLE `race_result` ADD COLUMN `temperature_celsius` REAL");
            db.execSQL("ALTER TABLE `race_result` ADD COLUMN `weather_condition` TEXT");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // Comma-separated interest tags e.g. "road,trail,marathon"
            db.execSQL("ALTER TABLE `runner_profile` ADD COLUMN `interests` TEXT");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // Elevation at race start in meters — from Open-Meteo geocoding
            db.execSQL("ALTER TABLE `race_result` ADD COLUMN `elevation_start_meters` INTEGER");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // Separate temperature unit preference ("celsius" or "fahrenheit")
            db.execSQL("ALTER TABLE `runner_profile` ADD COLUMN `preferred_temp_unit` TEXT");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // Expand pending_match with per-result detail columns so each row
            // represents one individual race result rather than one site.
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `race_name` TEXT");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `race_date` TEXT");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `distance_label` TEXT");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `distance_meters` REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `location` TEXT");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `bib_number` TEXT");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `finish_time` TEXT");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `finish_seconds` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `overall_place` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `overall_total` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `pending_match` ADD COLUMN `raw_data` TEXT");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // Per-user preferences for discovery sources.
            // Covers both default sites (hidden flag) and custom user-added sites.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_site_pref` (" +
                "`site_id` TEXT NOT NULL, " +          // matches DEFAULT_SITES id or "custom_N"
                "`hidden` INTEGER NOT NULL DEFAULT 0, " +
                "`custom_name` TEXT, " +               // null for default sites
                "`custom_url` TEXT, " +                // null for default sites
                "`added_at` TEXT, " +
                "PRIMARY KEY(`site_id`))"
            );
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // Device-local UUID for backend identification (pre-auth)
            db.execSQL("ALTER TABLE `runner_profile` ADD COLUMN `user_id` TEXT");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase db) {
            // New table: candidate matches waiting for runner confirmation
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_match` (" +
                "`id` TEXT NOT NULL, " +
                "`deduplication_key` TEXT, " +
                "`site_id` TEXT, " +
                "`site_name` TEXT, " +
                "`runner_name` TEXT, " +
                "`results_url` TEXT, " +
                "`result_count` INTEGER NOT NULL DEFAULT 0, " +
                "`notes` TEXT, " +
                "`status` TEXT, " +
                "`discovered_at` TEXT, " +
                "`updated_at` TEXT, " +
                "PRIMARY KEY(`id`))"
            );
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_match_deduplication_key` ON `pending_match` (`deduplication_key`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_match_status` ON `pending_match` (`status`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_match_discovered_at` ON `pending_match` (`discovered_at`)");

            // Track when discovery last ran and how many matches are waiting
            db.execSQL("ALTER TABLE `runner_profile` ADD COLUMN `last_discover_at` TEXT");
            db.execSQL("ALTER TABLE `runner_profile` ADD COLUMN `pending_count` INTEGER NOT NULL DEFAULT 0");
        }
    };
}
