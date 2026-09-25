/**
 * Revocable, expiring share links and the intentionally limited public vehicle history. Implemented in Phase 7.
 * <p>
 * Planned layers: {@code api} (REST controllers, request/response DTOs), {@code application}
 * (use cases, transactions, authorization checks), {@code domain} (entities, value objects, rules),
 * {@code infrastructure} (repositories, external adapters). Sub-packages are module-internal;
 * other modules may only use types exposed through an explicit {@code @NamedInterface}.
 */
@ApplicationModule(displayName = "Sharing")
package com.repairtrack.sharing;

import org.springframework.modulith.ApplicationModule;
