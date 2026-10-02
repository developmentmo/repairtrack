-- "Vehicles a garage registered" (GET /api/v1/garages/{id}/vehicles, history visibility).
CREATE INDEX ix_vehicle_registered_by_garage_id ON vehicle (registered_by_garage_id);
