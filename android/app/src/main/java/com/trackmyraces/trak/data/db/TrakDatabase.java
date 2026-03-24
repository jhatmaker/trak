package com.trackmyraces.trak.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.trackmyraces.trak.data.db.dao.RaceResultDao;
import com.trackmyraces.trak.data.db.dao.RunnerProfileDao;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;

/**
 * TrakDatabase — Room database for the Trak app.
 *
 * Single instance per process (double-checked locking singleton).
 * All writes happen on background threads; Room enforces this.
 *
 * Version history:
 *   1 — initial schema
 */
@Database(
    entities = {
        RunnerProfileEntity.class,
        RaceResultEntity.class,
        // ResultSplitEntity, ResultClaimEntity, CredentialEntryEntity, SavedViewEntity
        // are defined in TrakEntities.java — add them here as separate classes when split out
    },
    version = 1,
    exportSchema = true
)
public abstract class TrakDatabase extends RoomDatabase {

    private static final String DB_NAME = "trak.db";
    private static volatile TrakDatabase sInstance;

    // ── DAOs ──────────────────────────────────────────────────────────────

    public abstract RunnerProfileDao runnerProfileDao();
    public abstract RaceResultDao    raceResultDao();

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
                        .fallbackToDestructiveMigration() // dev only — replace with migrations before release
                        .build();
                }
            }
        }
        return sInstance;
    }

    // ── Migrations (add as schema evolves) ────────────────────────────────

    /**
     * Example migration template — copy and increment version numbers as needed.
     *
     * static final Migration MIGRATION_1_2 = new Migration(1, 2) {
     *     @Override
     *     public void migrate(@NonNull SupportSQLiteDatabase db) {
     *         db.execSQL("ALTER TABLE race_result ADD COLUMN age_grade_percent REAL");
     *     }
     * };
     */
}
