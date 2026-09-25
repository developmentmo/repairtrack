/**
 * Document metadata, S3-compatible object storage, SHA-256 integrity hashes and presigned access. Implemented in Phase 6.
 * <p>
 * Planned layers: {@code api} (REST controllers, request/response DTOs), {@code application}
 * (use cases, transactions, authorization checks), {@code domain} (entities, value objects, rules),
 * {@code infrastructure} (repositories, external adapters). Sub-packages are module-internal;
 * other modules may only use types exposed through an explicit {@code @NamedInterface}.
 */
@ApplicationModule(displayName = "Document")
package com.repairtrack.document;

import org.springframework.modulith.ApplicationModule;
