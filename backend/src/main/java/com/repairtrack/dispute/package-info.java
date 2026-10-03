/**
 * Ownership disputes (Phase 11): someone who says they are the rightful owner contests the current owner's claim;
 * the current owner can respond; a system admin decides. An upheld dispute revokes the contested ownership
 * (kept in the history) and makes the claimant the owner. Evidence files are private to system admins.
 * <p>
 * Layers as in the other modules; other modules may only use types in this base package.
 */
@ApplicationModule(displayName = "Dispute")
package com.repairtrack.dispute;

import org.springframework.modulith.ApplicationModule;
