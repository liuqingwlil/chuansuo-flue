package com.chuansuo.app;

import com.chuansuo.utils.HistoryPolicy;
import com.chuansuo.utils.MapUtils;
import com.chuansuo.utils.SearchPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationRegressionTest {
    @Test public void beijingCoordinatesKeepLongitudeFirst() {
        double[] bd = MapUtils.wgs2bd09(116.397, 39.908);
        assertEquals(116.409616, bd[0], 0.00003);
        assertEquals(39.915744, bd[1], 0.00003);
        double[] wgs = MapUtils.bd2wgs(bd[0], bd[1]);
        assertEquals(116.397, wgs[0], 0.00003);
        assertEquals(39.908, wgs[1], 0.00003);
    }

    @Test public void configuredRetentionChangesCleanupCutoff() {
        long now = 1800000000L;
        assertEquals(now - 2 * 86400L, HistoryPolicy.cutoffSeconds("2", now));
        assertEquals(now - 30 * 86400L, HistoryPolicy.cutoffSeconds("30", now));
    }

    @Test public void fractionalDaysAreSupported() {
        assertEquals(1800000000L - 43200, HistoryPolicy.cutoffSeconds("0.5", 1800000000L));
    }

    @Test public void invalidRetentionFallsBackToSevenDays() {
        for (String value : new String[]{null, "", "abc", "NaN", "Infinity", "-1", "0", "1e99"}) {
            assertEquals(1800000000L - 7 * 86400L,
                    HistoryPolicy.cutoffSeconds(value, 1800000000L));
        }
    }

    @Test public void locationFailureStillProvidesRequiredSearchCity() {
        assertEquals("北京", SearchPolicy.resolveCity(null, null));
        assertEquals("北京", SearchPolicy.resolveCity(" ", ""));
    }

    @Test public void configuredCityTakesPriorityOverDeviceCity() {
        assertEquals("上海", SearchPolicy.resolveCity(" 上海 ", "北京"));
    }

    @Test public void deviceCityIsUsedWithoutOverride() {
        assertEquals("济南市", SearchPolicy.resolveCity("", " 济南市 "));
    }
}
