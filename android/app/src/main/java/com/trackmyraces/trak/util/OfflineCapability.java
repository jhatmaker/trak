package com.trackmyraces.trak.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents whether a repository method or ViewModel action works offline.
 *
 * Every method that touches the network must be annotated.
 *
 * Examples:
 *   @OfflineCapability(available = false, reason = "Requires Anthropic API")
 *   public void extractResult(...) { ... }
 *
 *   @OfflineCapability(available = true)
 *   public LiveData<List<RaceResultEntity>> getAllResults() { ... }
 *
 *   @OfflineCapability(available = false, queued = true, reason = "Syncs on reconnect")
 *   public void updateResultNotes(...) { ... }
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface OfflineCapability {
    /** True if the method works without network access (e.g. reads from Room). */
    boolean available();

    /**
     * When available=false, true means the operation is queued locally and
     * will execute automatically when connectivity is restored.
     */
    boolean queued() default false;

    /** Human-readable explanation, e.g. "Requires Anthropic API". */
    String reason() default "";
}
