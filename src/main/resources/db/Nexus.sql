-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
-- -----------------------------------------------------
-- Schema nexus
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema nexus
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `nexus` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci ;
USE `nexus` ;

-- -----------------------------------------------------
-- Table `nexus`.`users`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`users` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `email_confirmed` TINYINT(1) NOT NULL DEFAULT '0',
  `password_hash` VARCHAR(255) NOT NULL,
  `display_name` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(255) NULL DEFAULT NULL,
  `link_code` VARCHAR(32) NOT NULL,
  `link_code_version` INT UNSIGNED NOT NULL DEFAULT '1',
  `link_code_updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `fcm_token` VARCHAR(500) NULL DEFAULT NULL,
  `last_login_at` DATETIME(3) NULL DEFAULT NULL,
  `failed_login_attempts` INT NOT NULL DEFAULT '0',
  `account_locked_until` TIMESTAMP NULL DEFAULT NULL,
  `birth_date` DATE NOT NULL,
  `terms_accepted` TINYINT(1) NOT NULL DEFAULT '0',
  `terms_accepted_at` DATETIME(3) NULL DEFAULT NULL,
  `account_status` VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_users_email` (`email` ASC) VISIBLE,
  UNIQUE INDEX `uk_users_link_code` (`link_code` ASC) VISIBLE,
  INDEX `idx_users_deleted` (`deleted_at` ASC) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 10
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_events`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(240) NOT NULL,
  `description` TEXT NULL DEFAULT NULL,
  `location` VARCHAR(255) NULL DEFAULT NULL,
  `starts_at_utc` DATETIME(3) NOT NULL,
  `ends_at_utc` DATETIME(3) NOT NULL,
  `start_tzid` VARCHAR(64) NOT NULL,
  `end_tzid` VARCHAR(64) NOT NULL,
  `all_day` TINYINT(1) NOT NULL DEFAULT '0',
  `status` ENUM('pending', 'approved', 'rejected', 'cancelled') NOT NULL DEFAULT 'pending',
  `created_by` INT UNSIGNED NOT NULL,
  `suggested_by` ENUM('system', 'user') NOT NULL DEFAULT 'system',
  `suggestion_score` DECIMAL(5,2) NULL DEFAULT NULL,
  `suggestion_context` JSON NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_time` (`starts_at_utc` ASC, `ends_at_utc` ASC) VISIBLE,
  INDEX `idx_status` (`status` ASC) VISIBLE,
  INDEX `fk_ae_creator` (`created_by` ASC) VISIBLE,
  CONSTRAINT `fk_ae_creator`
    FOREIGN KEY (`created_by`)
    REFERENCES `nexus`.`users` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_event_approvals`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_event_approvals` (
  `event_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `response` ENUM('pending', 'approved', 'rejected') NOT NULL DEFAULT 'pending',
  `responded_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`event_id`, `user_id`),
  INDEX `fk_aea_user` (`user_id` ASC) VISIBLE,
  CONSTRAINT `fk_aea_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`app_events` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_aea_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_event_chat_log`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_event_chat_log` (
  `event_id` BIGINT UNSIGNED NOT NULL,
  `mime_type` VARCHAR(120) NOT NULL,
  `bytes_size` BIGINT UNSIGNED NOT NULL,
  `content` LONGBLOB NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`),
  CONSTRAINT `fk_aecl_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`app_events` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_event_chat_messages`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_event_chat_messages` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT UNSIGNED NOT NULL,
  `author_id` INT UNSIGNED NULL DEFAULT NULL,
  `author_type` ENUM('user', 'assistant') NOT NULL DEFAULT 'user',
  `body` TEXT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_chat_event_time` (`event_id` ASC, `created_at` ASC) VISIBLE,
  INDEX `fk_aecm_user` (`author_id` ASC) VISIBLE,
  CONSTRAINT `fk_aecm_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`app_events` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_aecm_user`
    FOREIGN KEY (`author_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE SET NULL)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_event_participants`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_event_participants` (
  `event_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `role` ENUM('owner', 'guest') NOT NULL DEFAULT 'guest',
  PRIMARY KEY (`event_id`, `user_id`),
  INDEX `idx_ev_user` (`user_id` ASC) VISIBLE,
  CONSTRAINT `fk_aep_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`app_events` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_aep_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_event_photos`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_event_photos` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT UNSIGNED NOT NULL,
  `uploaded_by` INT UNSIGNED NULL DEFAULT NULL,
  `mime_type` VARCHAR(120) NOT NULL,
  `bytes_size` BIGINT UNSIGNED NOT NULL,
  `content` LONGBLOB NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_photo_event` (`event_id` ASC) VISIBLE,
  INDEX `fk_aep_photo_user` (`uploaded_by` ASC) VISIBLE,
  CONSTRAINT `fk_aep_photo_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`app_events` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_aep_photo_user`
    FOREIGN KEY (`uploaded_by`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE SET NULL)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`app_event_ratings`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`app_event_ratings` (
  `event_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `score` TINYINT UNSIGNED NOT NULL,
  `notes` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`, `user_id`),
  INDEX `fk_aer_user` (`user_id` ASC) VISIBLE,
  CONSTRAINT `fk_aer_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`app_events` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_aer_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`audit_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`audit_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NULL DEFAULT NULL,
  `action` VARCHAR(120) NOT NULL,
  `entity` VARCHAR(120) NOT NULL,
  `entity_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `metadata_json` JSON NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_audit_entity` (`entity` ASC, `entity_id` ASC) VISIBLE,
  INDEX `idx_audit_user` (`user_id` ASC) VISIBLE,
  CONSTRAINT `fk_audit_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`calendars`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`calendars` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `owner_user_id` INT UNSIGNED NOT NULL,
  `source` ENUM('local', 'google', 'outlook') NOT NULL DEFAULT 'local',
  `external_id` VARCHAR(190) NULL DEFAULT NULL,
  `name` VARCHAR(120) NOT NULL,
  `color_hex` CHAR(7) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_cal_owner` (`owner_user_id` ASC) VISIBLE,
  CONSTRAINT `fk_cal_owner`
    FOREIGN KEY (`owner_user_id`)
    REFERENCES `nexus`.`users` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`calendar_events`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`calendar_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `calendar_id` INT UNSIGNED NOT NULL,
  `title` VARCHAR(240) NOT NULL,
  `starts_at_utc` DATETIME(3) NOT NULL,
  `ends_at_utc` DATETIME(3) NOT NULL,
  `start_tzid` VARCHAR(64) NOT NULL,
  `end_tzid` VARCHAR(64) NOT NULL,
  `all_day` TINYINT(1) NOT NULL DEFAULT '0',
  `rrule` VARCHAR(600) NULL DEFAULT NULL,
  `rrule_dtstart_utc` DATETIME(3) NULL DEFAULT NULL,
  `rrule_until_utc` DATETIME(3) NULL DEFAULT NULL,
  `rrule_count` INT UNSIGNED NULL DEFAULT NULL,
  `visibility` ENUM('default', 'public', 'private') NOT NULL DEFAULT 'default',
  `status` ENUM('confirmed', 'tentative', 'cancelled') NOT NULL DEFAULT 'confirmed',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_ev_cal_time` (`calendar_id` ASC, `starts_at_utc` ASC, `ends_at_utc` ASC) VISIBLE,
  CONSTRAINT `fk_ce_calendar`
    FOREIGN KEY (`calendar_id`)
    REFERENCES `nexus`.`calendars` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`calendar_event_exceptions`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`calendar_event_exceptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT UNSIGNED NOT NULL,
  `occurrence_start_utc` DATETIME(3) NOT NULL,
  `is_cancelled` TINYINT(1) NOT NULL DEFAULT '0',
  `new_starts_at_utc` DATETIME(3) NULL DEFAULT NULL,
  `new_ends_at_utc` DATETIME(3) NULL DEFAULT NULL,
  `new_start_tzid` VARCHAR(64) NULL DEFAULT NULL,
  `new_end_tzid` VARCHAR(64) NULL DEFAULT NULL,
  `new_title` VARCHAR(240) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_cex_event` (`event_id` ASC, `occurrence_start_utc` ASC) VISIBLE,
  CONSTRAINT `fk_cex_event`
    FOREIGN KEY (`event_id`)
    REFERENCES `nexus`.`calendar_events` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`databasechangelog`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`databasechangelog` (
  `ID` VARCHAR(255) NOT NULL,
  `AUTHOR` VARCHAR(255) NOT NULL,
  `FILENAME` VARCHAR(255) NOT NULL,
  `DATEEXECUTED` DATETIME NOT NULL,
  `ORDEREXECUTED` INT NOT NULL,
  `EXECTYPE` VARCHAR(10) NOT NULL,
  `MD5SUM` VARCHAR(35) NULL DEFAULT NULL,
  `DESCRIPTION` VARCHAR(255) NULL DEFAULT NULL,
  `COMMENTS` VARCHAR(255) NULL DEFAULT NULL,
  `TAG` VARCHAR(255) NULL DEFAULT NULL,
  `LIQUIBASE` VARCHAR(20) NULL DEFAULT NULL,
  `CONTEXTS` VARCHAR(255) NULL DEFAULT NULL,
  `LABELS` VARCHAR(255) NULL DEFAULT NULL,
  `DEPLOYMENT_ID` VARCHAR(10) NULL DEFAULT NULL)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`databasechangeloglock`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`databasechangeloglock` (
  `ID` INT NOT NULL,
  `LOCKED` TINYINT NOT NULL,
  `LOCKGRANTED` DATETIME NULL DEFAULT NULL,
  `LOCKEDBY` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`ID`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`emotion_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`emotion_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `emotion_code` VARCHAR(50) NOT NULL,
  `intensity` TINYINT UNSIGNED NULL DEFAULT NULL,
  `notes` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_em_user_time` (`user_id` ASC, `occurred_at` ASC) VISIBLE,
  CONSTRAINT `fk_em_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`login_attempts`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`login_attempts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `attempt_count` INT NOT NULL,
  `blocked_until` DATETIME(6) NULL DEFAULT NULL,
  `email` VARCHAR(255) NOT NULL,
  `is_blocked` BIT(1) NOT NULL,
  `last_attempt_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`pref_categories`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`pref_categories` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_cat_name` (`name` ASC) VISIBLE,
  INDEX `idx_cat_deleted` (`deleted_at` ASC) VISIBLE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`preferences`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`preferences` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category_id` INT UNSIGNED NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `description` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_pref_cat_name` (`category_id` ASC, `name` ASC) VISIBLE,
  INDEX `idx_pref_category` (`category_id` ASC) VISIBLE,
  INDEX `idx_pref_deleted` (`deleted_at` ASC) VISIBLE,
  CONSTRAINT `fk_pref_category`
    FOREIGN KEY (`category_id`)
    REFERENCES `nexus`.`pref_categories` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`tags`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`tags` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) NOT NULL,
  `color_hex` CHAR(7) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tag_name` (`name` ASC) VISIBLE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`taggings`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`taggings` (
  `tag_id` INT UNSIGNED NOT NULL,
  `entity_type` ENUM('user', 'event', 'calendar', 'preference') NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`tag_id`, `entity_type`, `entity_id`),
  INDEX `idx_tagg_entity` (`entity_type` ASC, `entity_id` ASC) VISIBLE,
  CONSTRAINT `fk_tagg_tag`
    FOREIGN KEY (`tag_id`)
    REFERENCES `nexus`.`tags` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`user_app_config`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`user_app_config` (
  `user_id` INT UNSIGNED NOT NULL,
  `settings` JSON NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_uac_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`user_link_code_history`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`user_link_code_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `old_code` VARCHAR(255) NULL DEFAULT NULL,
  `new_code` VARCHAR(255) NOT NULL,
  `reason` VARCHAR(255) NULL DEFAULT NULL,
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  INDEX `idx_hist_user` (`user_id` ASC, `changed_at` ASC) VISIBLE,
  CONSTRAINT `fk_ulch_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`user_links`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`user_links` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `initiator_user_id` INT UNSIGNED NOT NULL,
  `partner_user_id` INT UNSIGNED NOT NULL,
  `code_in_use` VARCHAR(32) NOT NULL,
  `is_active` TINYINT(1) NOT NULL DEFAULT '1',
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ended_at` DATETIME(3) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL DEFAULT NULL,
  `couple_key` VARCHAR(64) GENERATED ALWAYS AS (concat(lpad(least(`initiator_user_id`,`partner_user_id`),10,_utf8mb4'0'),_utf8mb4'-',lpad(greatest(`initiator_user_id`,`partner_user_id`),10,_utf8mb4'0'),_utf8mb4':',`is_active`)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_couple_active` (`couple_key` ASC) VISIBLE,
  INDEX `idx_partner` (`partner_user_id` ASC, `is_active` ASC) VISIBLE,
  INDEX `idx_code_in_use` (`code_in_use` ASC) VISIBLE,
  INDEX `fk_ul_init` (`initiator_user_id` ASC) VISIBLE,
  CONSTRAINT `fk_ul_init`
    FOREIGN KEY (`initiator_user_id`)
    REFERENCES `nexus`.`users` (`id`),
  CONSTRAINT `fk_ul_part`
    FOREIGN KEY (`partner_user_id`)
    REFERENCES `nexus`.`users` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`user_preferences`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`user_preferences` (
  `user_id` INT UNSIGNED NOT NULL,
  `preference_id` INT UNSIGNED NOT NULL,
  `level` TINYINT UNSIGNED NULL DEFAULT NULL,
  `notes` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`, `preference_id`),
  INDEX `idx_up_user` (`user_id` ASC) VISIBLE,
  INDEX `idx_up_pref` (`preference_id` ASC) VISIBLE,
  CONSTRAINT `fk_up_pref`
    FOREIGN KEY (`preference_id`)
    REFERENCES `nexus`.`preferences` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_up_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`user_profile`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`user_profile` (
  `user_id` INT UNSIGNED NOT NULL,
  `avatar_mime` VARCHAR(255) NULL DEFAULT NULL,
  `avatar_bytes` TINYBLOB NULL DEFAULT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_upro_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`user_roles`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`user_roles` (
  `user_id` BIGINT NOT NULL,
  `role` ENUM('ROLE_ADMIN', 'ROLE_AI', 'ROLE_USER') NULL DEFAULT NULL)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `nexus`.`verification_tokens`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `nexus`.`verification_tokens` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `token` VARCHAR(255) NOT NULL,
  `purpose` ENUM('email_confirm', 'password_reset') NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` DATETIME(3) NOT NULL,
  `consumed_at` DATETIME(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_token` (`token` ASC) VISIBLE,
  INDEX `idx_user_purpose` (`user_id` ASC, `purpose` ASC) VISIBLE,
  CONSTRAINT `fk_vt_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `nexus`.`users` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;

USE `nexus` ;

-- -----------------------------------------------------
-- procedure sp_link_users_by_code
-- -----------------------------------------------------

DELIMITER $$
USE `nexus`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `sp_link_users_by_code`(
  IN  p_initiator_user_id INT UNSIGNED,
  IN  p_partner_link_code VARCHAR(32)
)
BEGIN
  DECLARE v_partner_id INT UNSIGNED;
  DECLARE v_initiator_code VARCHAR(32);

  START TRANSACTION;

  SELECT id INTO v_partner_id
  FROM users
  WHERE link_code = p_partner_link_code
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_partner_id IS NULL OR v_partner_id = p_initiator_user_id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Link code inválido o auto-vínculo no permitido';
  END IF;

  SELECT link_code INTO v_initiator_code
  FROM users
  WHERE id = p_initiator_user_id
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_initiator_code IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Iniciador inexistente';
  END IF;

  UPDATE users
  SET link_code            = v_initiator_code,
      link_code_version    = link_code_version + 1,
      link_code_updated_at = UTC_TIMESTAMP(3)
  WHERE id = v_partner_id;

  INSERT INTO user_links (initiator_user_id, partner_user_id, code_in_use, is_active, started_at)
  VALUES (p_initiator_user_id, v_partner_id, v_initiator_code, 1, UTC_TIMESTAMP(3));

  COMMIT;
END$$

DELIMITER ;

-- -----------------------------------------------------
-- procedure sp_sync_event_status
-- -----------------------------------------------------

DELIMITER $$
USE `nexus`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `sp_sync_event_status`(IN p_event_id BIGINT UNSIGNED)
BEGIN
  DECLARE v_participants INT UNSIGNED DEFAULT 0;
  DECLARE v_approved     INT UNSIGNED DEFAULT 0;
  DECLARE v_rejected     INT UNSIGNED DEFAULT 0;

  SELECT COUNT(*) INTO v_participants
  FROM app_event_participants
  WHERE event_id = p_event_id;

  SELECT
    COALESCE(SUM(response='approved'),0),
    COALESCE(SUM(response='rejected'),0)
  INTO v_approved, v_rejected
  FROM app_event_approvals
  WHERE event_id = p_event_id;

  IF v_rejected > 0 THEN
    UPDATE app_events SET status = 'rejected' WHERE id = p_event_id;
  ELSEIF v_participants > 0 AND v_approved >= v_participants THEN
    UPDATE app_events SET status = 'approved' WHERE id = p_event_id;
  ELSE
    UPDATE app_events SET status = 'pending' WHERE id = p_event_id;
  END IF;
END$$

DELIMITER ;
USE `nexus`;

DELIMITER $$
USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`users_set_updated`
BEFORE UPDATE ON `nexus`.`users`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`app_events_set_updated`
BEFORE UPDATE ON `nexus`.`app_events`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`aea_before_update`
BEFORE UPDATE ON `nexus`.`app_event_approvals`
FOR EACH ROW
BEGIN
  IF NEW.response <> OLD.response THEN
    SET NEW.responded_at = UTC_TIMESTAMP(3);
  END IF;
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`app_event_chat_log_set_updated`
BEFORE UPDATE ON `nexus`.`app_event_chat_log`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`aep_after_delete`
AFTER DELETE ON `nexus`.`app_event_participants`
FOR EACH ROW
BEGIN
  DELETE FROM app_event_approvals
   WHERE event_id = OLD.event_id AND user_id = OLD.user_id;
  CALL sp_sync_event_status(OLD.event_id);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`aep_after_insert`
AFTER INSERT ON `nexus`.`app_event_participants`
FOR EACH ROW
BEGIN
  INSERT IGNORE INTO app_event_approvals(event_id, user_id)
  VALUES (NEW.event_id, NEW.user_id);
  CALL sp_sync_event_status(NEW.event_id);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`calendars_set_updated`
BEFORE UPDATE ON `nexus`.`calendars`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`calendar_events_set_updated`
BEFORE UPDATE ON `nexus`.`calendar_events`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`calendar_event_exceptions_set_updated`
BEFORE UPDATE ON `nexus`.`calendar_event_exceptions`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`pref_categories_set_updated`
BEFORE UPDATE ON `nexus`.`pref_categories`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`preferences_set_updated`
BEFORE UPDATE ON `nexus`.`preferences`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`tags_set_updated`
BEFORE UPDATE ON `nexus`.`tags`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`user_app_config_set_updated`
BEFORE UPDATE ON `nexus`.`user_app_config`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`user_links_set_updated`
BEFORE UPDATE ON `nexus`.`user_links`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`user_preferences_set_updated`
BEFORE UPDATE ON `nexus`.`user_preferences`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$

USE `nexus`$$
CREATE
DEFINER=`root`@`localhost`
TRIGGER `nexus`.`user_profile_set_updated`
BEFORE UPDATE ON `nexus`.`user_profile`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = UTC_TIMESTAMP(3);
END$$


DELIMITER ;

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
