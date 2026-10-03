package com.repairtrack.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} maintenance jobs (e.g. refresh-token cleanup). Jobs must be idempotent: with
 * several application instances each instance runs them.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {
}
