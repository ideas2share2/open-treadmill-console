ALTER TABLE workouts ADD COLUMN walk_ms INTEGER NOT NULL DEFAULT 0;
ALTER TABLE workouts ADD COLUMN run_ms INTEGER NOT NULL DEFAULT 0;
ALTER TABLE workouts ADD COLUMN walk_meters REAL NOT NULL DEFAULT 0;
ALTER TABLE workouts ADD COLUMN run_meters REAL NOT NULL DEFAULT 0;
UPDATE workouts SET walk_ms=CASE WHEN kind='Run' THEN 0 ELSE moving_ms END,run_ms=CASE WHEN kind='Run' THEN moving_ms ELSE 0 END,walk_meters=CASE WHEN kind='Run' THEN 0 ELSE meters END,run_meters=CASE WHEN kind='Run' THEN meters ELSE 0 END;
