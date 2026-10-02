/**
 * Derives source type and verification status server-side; clients can never set them. Implemented in Phase 5.
 * <p>
 * Planned layers: {@code api} (REST controllers, request/response DTOs), {@code application}
 * (use cases, transactions, authorization checks), {@code domain} (entities, value objects, rules),
 * {@code infrastructure} (repositories, external adapters). Sub-packages are module-internal;
 * other modules may only use types in the module's base package
 * (its public API) or in a package explicitly marked {@code @NamedInterface}.
 */
@ApplicationModule(displayName = "Verification")
package com.repairtrack.verification;

import org.springframework.modulith.ApplicationModule;
