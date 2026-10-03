package com.repairtrack.dispute.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code repairtrack.disputes.*}
 *
 * @param responseTime         how long the current owner has to respond (14 days)
 * @param maxOpenPerClaimant   undecided disputes one user may have at the same time
 * @param maxEvidencePerParty  files each party may submit per dispute
 */
@ConfigurationProperties(prefix = "repairtrack.disputes")
public record DisputeProperties(Duration responseTime, Integer maxOpenPerClaimant, Integer maxEvidencePerParty) {

    public DisputeProperties {
        responseTime = responseTime == null ? Duration.ofDays(14) : responseTime;
        maxOpenPerClaimant = maxOpenPerClaimant == null ? 3 : maxOpenPerClaimant;
        maxEvidencePerParty = maxEvidencePerParty == null ? 5 : maxEvidencePerParty;
    }
}
