package com.repairtrack.mileage.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.repairtrack.mileage.MileageAnomaly;
import com.repairtrack.mileage.MileageReading;

/**
 * Finds inconsistencies in a chronologically ordered list of readings.
 * <p>
 * Rule: a reading lower than the directly preceding reading is an anomaly. Comparing with the
 * direct predecessor (not with the highest earlier value) is deliberately conservative: one
 * mistyped high value then produces one warning instead of flagging every later reading.
 * Analysis runs on read over the full timeline, so inserting a backdated record re-evaluates
 * its neighbours and no stored flag can become stale.
 */
public final class MileageAnalyzer {

    private MileageAnalyzer() {
    }

    /** @param chronological readings ordered by recorded date, then by creation time */
    public static List<MileageAnomaly> analyze(List<MileageReading> chronological) {
        List<MileageAnomaly> anomalies = new ArrayList<>();
        for (int i = 1; i < chronological.size(); i++) {
            MileageReading earlier = chronological.get(i - 1);
            MileageReading later = chronological.get(i);
            if (later.mileage() < earlier.mileage()) {
                anomalies.add(new MileageAnomaly(MileageAnomaly.MILEAGE_DECREASE, String.format(Locale.ROOT,
                        "The mileage of %d km on %s is lower than the previously registered %d km on %s.",
                        later.mileage(), later.recordedDate(), earlier.mileage(), earlier.recordedDate()),
                        earlier, later));
            }
        }
        return List.copyOf(anomalies);
    }
}
