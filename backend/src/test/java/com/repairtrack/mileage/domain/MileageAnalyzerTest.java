package com.repairtrack.mileage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.repairtrack.mileage.MileageAnomaly;
import com.repairtrack.mileage.MileageReading;
import com.repairtrack.verification.SourceType;

class MileageAnalyzerTest {

    @Test
    void increasingReadingsAreConsistent() {
        assertThat(MileageAnalyzer.analyze(List.of(
                reading(100_000, "2023-01-10"),
                reading(120_000, "2024-01-10"),
                reading(120_000, "2024-02-10")))).isEmpty();
    }

    @Test
    void decreaseIsReportedNeutrallyWithBothReadings() {
        MileageReading earlier = reading(150_000, "2024-01-10");
        MileageReading later = reading(140_000, "2025-01-10");

        List<MileageAnomaly> anomalies = MileageAnalyzer.analyze(List.of(earlier, later));

        assertThat(anomalies).hasSize(1);
        MileageAnomaly anomaly = anomalies.getFirst();
        assertThat(anomaly.code()).isEqualTo(MileageAnomaly.MILEAGE_DECREASE);
        assertThat(anomaly.earlier()).isEqualTo(earlier);
        assertThat(anomaly.later()).isEqualTo(later);
        assertThat(anomaly.message()).contains("140000").contains("150000").doesNotContainIgnoringCase("fraud");
    }

    @Test
    void singleMistypedHighValueProducesOneWarningNotOnePerLaterReading() {
        // 1_000_000 is a typo for 100_000; only the step after it is inconsistent
        List<MileageAnomaly> anomalies = MileageAnalyzer.analyze(List.of(
                reading(90_000, "2022-01-01"),
                reading(1_000_000, "2023-01-01"),
                reading(110_000, "2024-01-01"),
                reading(120_000, "2025-01-01")));

        assertThat(anomalies).hasSize(1);
    }

    @Test
    void emptyAndSingleHistoriesHaveNoAnomalies() {
        assertThat(MileageAnalyzer.analyze(List.of())).isEmpty();
        assertThat(MileageAnalyzer.analyze(List.of(reading(1, "2020-01-01")))).isEmpty();
    }

    private static MileageReading reading(int km, String date) {
        return new MileageReading(UUID.randomUUID(), km, LocalDate.parse(date), SourceType.GARAGE, UUID.randomUUID());
    }
}
