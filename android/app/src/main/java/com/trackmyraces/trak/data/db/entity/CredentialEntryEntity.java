package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Metadata for a saved credential entry (club/timing site login).
 *
 * SECURITY: The password is NEVER stored here.
 * It is stored exclusively in Android Keystore via CredentialManager.
 * Only the Keystore alias is stored here so we can retrieve it.
 *
 * This entity IS synced to DynamoDB (username + site URL only — no password).
 */
@Entity(
    tableName = "credential_entry",
    indices = { @Index(value = {"site_url"}, unique = true) }
)
public class CredentialEntryEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /** Base URL of the site e.g. "https://club.example.com" */
    @ColumnInfo(name = "site_url")
    public String siteUrl;

    /** Human-readable label e.g. "Quincy Running Club" */
    @ColumnInfo(name = "site_label")
    public String siteLabel;

    @ColumnInfo(name = "username")
    public String username;

    /**
     * Android Keystore alias for the encrypted password.
     * Use CredentialManager.getPassword(keystoreAlias) to retrieve.
     * Format: "trak_cred_{id}"
     */
    @ColumnInfo(name = "keystore_alias")
    public String keystoreAlias;

    /** "ok" | "expired" | "failed" | "untested" */
    @ColumnInfo(name = "login_status")
    public String loginStatus;

    @ColumnInfo(name = "last_login_at")
    public String lastLoginAt;

    /** ISO timestamp when the cached session cookie expires */
    @ColumnInfo(name = "cookie_expiry")
    public String cookieExpiry;

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;
}
