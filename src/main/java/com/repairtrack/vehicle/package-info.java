/**
 * Vehicles (VIN as primary identity) and ownership over time (VehicleOwnership). Implemented in Phase 4.
 * <p>
 * Planned layers: {@code api} (REST controllers, request/response DTOs), {@code application}
 * (use cases, transactions, authorization checks), {@code domain} (entities, value objects, rules),
 * {@code infrastructure} (repositories, external adapters). Sub-packages are module-internal;
 * other modules may only use types in the module's base package
 * (its public API) or in a package explicitly marked {@code @NamedInterface}.
 */
@ApplicationModule(displayName = "Vehicle")
package com.repairtrack.vehicle;

import org.springframework.modulith.ApplicationModule;
