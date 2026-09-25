/**
 * Garages, garage users (GARAGE_ADMIN, MECHANIC) and garage verification. Implemented in Phase 3.
 * <p>
 * Planned layers: {@code api} (REST controllers, request/response DTOs), {@code application}
 * (use cases, transactions, authorization checks), {@code domain} (entities, value objects, rules),
 * {@code infrastructure} (repositories, external adapters). Sub-packages are module-internal;
 * other modules may only use types exposed through an explicit {@code @NamedInterface}.
 */
@ApplicationModule(displayName = "Garage")
package com.repairtrack.garage;

import org.springframework.modulith.ApplicationModule;
