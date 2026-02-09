-- Add quartz_schedule_id column to schedules table for Quartz integration
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS quartz_schedule_id VARCHAR(100);
