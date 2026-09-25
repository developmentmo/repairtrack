/**
 * Shared kernel: cross-cutting technical building blocks (error model, time source).
 * <p>
 * Declared OPEN so every module may use its sub-packages. Keep this module small:
 * it must never contain business concepts (vehicles, repairs, garages, ...).
 * If something here starts to carry domain meaning, it belongs in a feature module.
 */
@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN)
package com.repairtrack.common;

import org.springframework.modulith.ApplicationModule;
