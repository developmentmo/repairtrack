/**
 * Repair events and parts: the core, append-only vehicle history with void and correction instead of delete. Implemented in Phase 5.
 * <p>
 * Planned layers: {@code api} (REST controllers, request/response DTOs), {@code application}
 * (use cases, transactions, authorization checks), {@code domain} (entities, value objects, rules),
 * {@code infrastructure} (repositories, external adapters). Sub-packages are module-internal;
 * other modules may only use types exposed through an explicit {@code @NamedInterface}.
 */
@ApplicationModule(displayName = "Repair")
package com.repairtrack.repair;

import org.springframework.modulith.ApplicationModule;
