package com.trackmyraces.trak.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for TimeFormatter — no Android dependencies, runs on JVM.
 */
public class TimeFormatterTest {

    @Test
    public void secondsToTime_hoursMinutesSeconds() {
        assertEquals("1:23:45", TimeFormatter.secondsToTime(5025));
        assertEquals("3:00:00", TimeFormatter.secondsToTime(10800));
        assertEquals("1:00:00", TimeFormatter.secondsToTime(3600));
    }

    @Test
    public void secondsToTime_minutesOnly() {
        assertEquals("22:15", TimeFormatter.secondsToTime(1335));
        assertEquals("1:00",  TimeFormatter.secondsToTime(60));
        assertEquals("0:30",  TimeFormatter.secondsToTime(30));
    }

    @Test
    public void secondsToTime_zero() {
        assertEquals("--:--", TimeFormatter.secondsToTime(0));
        assertEquals("--:--", TimeFormatter.secondsToTime(-1));
    }

    @Test
    public void timeToSeconds_hhmmss() {
        assertEquals(5025,  TimeFormatter.timeToSeconds("1:23:45"));
        assertEquals(10800, TimeFormatter.timeToSeconds("3:00:00"));
    }

    @Test
    public void timeToSeconds_mmss() {
        assertEquals(1335, TimeFormatter.timeToSeconds("22:15"));
        assertEquals(60,   TimeFormatter.timeToSeconds("1:00"));
    }

    @Test
    public void timeToSeconds_stripsSubSeconds() {
        assertEquals(5025, TimeFormatter.timeToSeconds("1:23:45.3"));
        assertEquals(1335, TimeFormatter.timeToSeconds("22:15.0"));
    }

    @Test
    public void timeToSeconds_invalidReturnsNegative() {
        assertEquals(-1, TimeFormatter.timeToSeconds(null));
        assertEquals(-1, TimeFormatter.timeToSeconds(""));
        assertEquals(-1, TimeFormatter.timeToSeconds("abc"));
    }

    @Test
    public void pacePerKm_formats() {
        assertEquals("4:27/km", TimeFormatter.pacePerKm(267));
        assertEquals("5:00/km", TimeFormatter.pacePerKm(300));
    }

    @Test
    public void pacePerKm_zero() {
        assertEquals("--:--/km", TimeFormatter.pacePerKm(0));
    }

    @Test
    public void formatDistance_metric() {
        assertEquals("42.2 km", TimeFormatter.formatDistance(42195, false));
        assertEquals("5.0 km",  TimeFormatter.formatDistance(5000,  false));
        assertEquals("21.1 km", TimeFormatter.formatDistance(21097, false));
    }

    @Test
    public void formatDistance_imperial() {
        assertEquals("26.2 mi", TimeFormatter.formatDistance(42195, true));
        assertEquals("3.1 mi",  TimeFormatter.formatDistance(5000,  true));
    }

    @Test
    public void formatBQGap_underStandard() {
        assertEquals("2:00 under BQ", TimeFormatter.formatBQGap(120));
        assertEquals("0:01 under BQ", TimeFormatter.formatBQGap(1));
        assertEquals("0:00 under BQ", TimeFormatter.formatBQGap(0));
    }

    @Test
    public void formatBQGap_overStandard() {
        assertEquals("0:45 over BQ",  TimeFormatter.formatBQGap(-45));
        assertEquals("10:00 over BQ", TimeFormatter.formatBQGap(-600));
    }

    @Test
    public void formatPlacePercentile() {
        assertEquals("Top 96%", TimeFormatter.formatPlacePercentile(14,  312));
        assertEquals("Top 50%", TimeFormatter.formatPlacePercentile(50,  100));
        assertEquals("Top 99%", TimeFormatter.formatPlacePercentile(1,   100));
        assertEquals("—",       TimeFormatter.formatPlacePercentile(0,   100));
        assertEquals("—",       TimeFormatter.formatPlacePercentile(10,  0));
    }

    @Test
    public void formatAgeGrade() {
        assertEquals("68.4%", TimeFormatter.formatAgeGrade(68.4));
        assertEquals("100.0%",TimeFormatter.formatAgeGrade(100.0));
        assertEquals("—",     TimeFormatter.formatAgeGrade(0));
    }
}
