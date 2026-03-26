package com.trackmyraces.trak.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.trackmyraces.trak.data.db.dao.CredentialEntryDao;
import com.trackmyraces.trak.data.db.dao.RaceResultDao;
import com.trackmyraces.trak.data.db.dao.ResultClaimDao;
import com.trackmyraces.trak.data.db.dao.ResultSplitDao;
import com.trackmyraces.trak.data.db.dao.RunnerProfileDao;
import com.trackmyraces.trak.data.db.dao.SavedViewDao;
import com.trackmyraces.trak.data.db.entity.CredentialEntryEntity;
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
 */
@Database(
    entities = {
        RunnerProfileEntity.class,
        RaceResultEntity.class,
        ResultSplitEntity.class,
        ResultClaimEntity.class,
        CredentialEntryEntity.class,
        SavedViewEntity.class,
    },
    version = 6,
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
}
