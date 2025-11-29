-- Migration: Add fcm_token column to users table
-- Purpose: Store Firebase Cloud Messaging tokens for push notifications
-- Date: 2025-01-XX

USE `nexus`;

-- Add fcm_token column
ALTER TABLE `nexus`.`users` 
ADD COLUMN `fcm_token` VARCHAR(500) NULL DEFAULT NULL 
AFTER `link_code_updated_at`;

-- Add index for faster token lookups
CREATE INDEX `idx_users_fcm_token` ON `nexus`.`users` (`fcm_token`);
