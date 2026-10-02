package com.repairtrack.vehicle;

/** One changed vehicle attribute, for the audit trail. Values are rendered as strings. */
public record VehicleFieldChange(String field, String oldValue, String newValue) {
}
