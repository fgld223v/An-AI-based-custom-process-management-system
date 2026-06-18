CREATE DATABASE IF NOT EXISTS ai_workflow_mvp
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_workflow_mvp;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS department (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id BIGINT UNSIGNED NULL,
  dept_code VARCHAR(64) NOT NULL,
  dept_name VARCHAR(128) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  leader_user_id BIGINT UNSIGNED NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_dept_code (dept_code),
  KEY idx_department_parent_id (parent_id),
  KEY idx_department_leader_user_id (leader_user_id),
  KEY idx_department_status (status),
  KEY idx_department_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Department';

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  department_id BIGINT UNSIGNED NULL,
  supervisor_id BIGINT UNSIGNED NULL,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NULL,
  nickname VARCHAR(64) NULL,
  real_name VARCHAR(64) NULL,
  phone VARCHAR(32) NULL,
  email VARCHAR(128) NULL,
  avatar_url VARCHAR(512) NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'USER',
  system_role ENUM('super_admin','biz_admin','normal_user') NOT NULL DEFAULT 'normal_user',
  managed_biz_type_ids JSON NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME NULL,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_department_id (department_id),
  KEY idx_sys_user_supervisor_id (supervisor_id),
  KEY idx_sys_user_role (role),
  KEY idx_sys_user_system_role (system_role),
  KEY idx_sys_user_enabled (enabled),
  KEY idx_sys_user_status (status),
  KEY idx_sys_user_deleted (deleted),
  KEY idx_sys_user_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System user';

CREATE TABLE IF NOT EXISTS biz_type_dict (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id BIGINT UNSIGNED NULL,
  type_code VARCHAR(64) NOT NULL,
  type_name VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_type_dict_type_code (type_code),
  KEY idx_biz_type_dict_parent_id (parent_id),
  KEY idx_biz_type_dict_enabled (enabled),
  KEY idx_biz_type_dict_deleted (deleted),
  KEY idx_biz_type_dict_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Business type dictionary';

CREATE TABLE IF NOT EXISTS form_definition (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  form_code VARCHAR(64) NOT NULL,
  form_name VARCHAR(128) NOT NULL,
  biz_type_id BIGINT UNSIGNED NULL,
  version INT NOT NULL DEFAULT 1,
  status ENUM('draft','published','disabled') NOT NULL DEFAULT 'draft',
  field_list JSON NULL,
  form_schema JSON NULL,
  created_by BIGINT UNSIGNED NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_definition_code_version (form_code, version),
  KEY idx_form_definition_form_code (form_code),
  KEY idx_form_definition_biz_type_id (biz_type_id),
  KEY idx_form_definition_status (status),
  KEY idx_form_definition_created_by (created_by),
  KEY idx_form_definition_deleted (deleted),
  KEY idx_form_definition_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Form definition';

CREATE TABLE IF NOT EXISTS process_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  biz_type_id BIGINT UNSIGNED NULL,
  form_id BIGINT UNSIGNED NULL,
  version INT NOT NULL DEFAULT 1,
  status ENUM('draft','reviewing','published','disabled') NOT NULL DEFAULT 'draft',
  source_type ENUM('ai_generated','manual','market_copy','fragment_combo') NOT NULL DEFAULT 'manual',
  bpmn_xml LONGTEXT NULL,
  node_config JSON NULL,
  form_bind_config JSON NULL,
  flowable_deployment_id VARCHAR(128) NULL,
  flowable_process_definition_id VARCHAR(128) NULL,
  created_by BIGINT UNSIGNED NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_template_code_version (template_code, version),
  KEY idx_process_template_template_code (template_code),
  KEY idx_process_template_biz_type_id (biz_type_id),
  KEY idx_process_template_form_id (form_id),
  KEY idx_process_template_status (status),
  KEY idx_process_template_flowable_definition_id (flowable_process_definition_id),
  KEY idx_process_template_created_by (created_by),
  KEY idx_process_template_deleted (deleted),
  KEY idx_process_template_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Process template';

CREATE TABLE IF NOT EXISTS workflow_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_name VARCHAR(128) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  form_json LONGTEXT NOT NULL,
  bpmn_xml LONGTEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT UNSIGNED NULL,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_workflow_template_business_type (business_type),
  KEY idx_workflow_template_status (status),
  KEY idx_workflow_template_created_by (created_by),
  KEY idx_workflow_template_deleted (deleted),
  KEY idx_workflow_template_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Legacy workflow template';

CREATE TABLE IF NOT EXISTS process_fragment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  fragment_code VARCHAR(64) NOT NULL,
  fragment_name VARCHAR(128) NOT NULL,
  biz_type_id BIGINT UNSIGNED NULL,
  description VARCHAR(1024) NULL,
  fragment_type VARCHAR(64) NOT NULL,
  status ENUM('draft','published','disabled') NOT NULL DEFAULT 'draft',
  bpmn_xml LONGTEXT NULL,
  node_config JSON NULL,
  created_by BIGINT UNSIGNED NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_fragment_fragment_code (fragment_code),
  KEY idx_process_fragment_biz_type_id (biz_type_id),
  KEY idx_process_fragment_fragment_type (fragment_type),
  KEY idx_process_fragment_status (status),
  KEY idx_process_fragment_created_by (created_by),
  KEY idx_process_fragment_deleted (deleted),
  KEY idx_process_fragment_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Process fragment';

CREATE TABLE IF NOT EXISTS template_fragment_ref (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  fragment_id BIGINT UNSIGNED NOT NULL,
  fragment_version INT NULL,
  sync_status ENUM('synced','pending_update','unbound') NOT NULL DEFAULT 'synced',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_fragment_ref (template_id, fragment_id),
  KEY idx_template_fragment_ref_template_id (template_id),
  KEY idx_template_fragment_ref_fragment_id (fragment_id),
  KEY idx_template_fragment_ref_sync_status (sync_status),
  KEY idx_template_fragment_ref_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Template fragment reference';

CREATE TABLE IF NOT EXISTS template_market (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  source_id BIGINT UNSIGNED NOT NULL,
  type ENUM('template','fragment') NOT NULL,
  title VARCHAR(128) NOT NULL,
  description VARCHAR(1024) NULL,
  cover_url VARCHAR(512) NULL,
  biz_type_id BIGINT UNSIGNED NULL,
  publisher_id BIGINT UNSIGNED NULL,
  use_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  tags JSON NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_template_market_source (type, source_id),
  KEY idx_template_market_biz_type_id (biz_type_id),
  KEY idx_template_market_publisher_id (publisher_id),
  KEY idx_template_market_deleted (deleted),
  KEY idx_template_market_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Template market';

CREATE TABLE IF NOT EXISTS flow_node (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  node_key VARCHAR(128) NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  node_type ENUM('start_event','end_event','user_task','exclusive_gateway','parallel_gateway','inclusive_gateway','sub_process','cc_node') NOT NULL,
  approver_type VARCHAR(64) NULL,
  approver_config JSON NULL,
  form_permission JSON NULL,
  node_config JSON NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_node_template_node_key (template_id, node_key),
  KEY idx_flow_node_template_id (template_id),
  KEY idx_flow_node_node_type (node_type),
  KEY idx_flow_node_deleted (deleted),
  KEY idx_flow_node_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flow node';

CREATE TABLE IF NOT EXISTS process_instance (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_code VARCHAR(64) NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  form_id BIGINT UNSIGNED NULL,
  applicant_id BIGINT UNSIGNED NOT NULL,
  biz_type_id BIGINT UNSIGNED NULL,
  title VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  form_data LONGTEXT NULL,
  current_node_key VARCHAR(128) NULL,
  current_node_name VARCHAR(128) NULL,
  current_business_type VARCHAR(64) NULL,
  flowable_process_instance_id VARCHAR(128) NULL,
  flowable_definition_id VARCHAR(128) NULL,
  flowable_deployment_id VARCHAR(128) NULL,
  started_at DATETIME NULL,
  ended_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_instance_code (instance_code),
  KEY idx_process_instance_template_id (template_id),
  KEY idx_process_instance_form_id (form_id),
  KEY idx_process_instance_applicant_id (applicant_id),
  KEY idx_process_instance_biz_type_id (biz_type_id),
  KEY idx_process_instance_status (status),
  KEY idx_process_instance_flowable_instance_id (flowable_process_instance_id),
  KEY idx_process_instance_flowable_definition_id (flowable_definition_id),
  KEY idx_process_instance_flowable_deployment_id (flowable_deployment_id),
  KEY idx_process_instance_deleted (deleted),
  KEY idx_process_instance_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Business process instance';

CREATE TABLE IF NOT EXISTS form_submission (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  process_instance_id BIGINT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  node_key VARCHAR(128) NOT NULL,
  node_name VARCHAR(128) NULL,
  business_type VARCHAR(64) NULL,
  form_id BIGINT UNSIGNED NOT NULL,
  form_data_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_submission_instance_node (process_instance_id, node_key),
  KEY idx_form_submission_instance_id (process_instance_id),
  KEY idx_form_submission_template_id (template_id),
  KEY idx_form_submission_form_id (form_id),
  KEY idx_form_submission_status (status),
  KEY idx_form_submission_deleted (deleted),
  KEY idx_form_submission_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Form submission';

CREATE TABLE IF NOT EXISTS task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  node_key VARCHAR(128) NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  assignee_id BIGINT UNSIGNED NULL,
  candidate_type VARCHAR(64) NULL,
  candidate_config JSON NULL,
  status ENUM('pending','processing','completed','delegated','timeout') NOT NULL DEFAULT 'pending',
  flowable_task_id VARCHAR(128) NULL,
  due_time DATETIME NULL,
  claimed_at DATETIME NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_task_instance_id (instance_id),
  KEY idx_task_template_id (template_id),
  KEY idx_task_assignee_id (assignee_id),
  KEY idx_task_status (status),
  KEY idx_task_flowable_task_id (flowable_task_id),
  KEY idx_task_due_time (due_time),
  KEY idx_task_deleted (deleted),
  KEY idx_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Business task';

CREATE TABLE IF NOT EXISTS approval_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  task_id BIGINT UNSIGNED NULL,
  node_key VARCHAR(128) NOT NULL,
  approver_id BIGINT UNSIGNED NOT NULL,
  action ENUM('approve','reject','supplement','delegate','transfer') NOT NULL,
  comment_text TEXT NULL,
  attachment_list JSON NULL,
  operated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_approval_record_instance_id (instance_id),
  KEY idx_approval_record_task_id (task_id),
  KEY idx_approval_record_approver_id (approver_id),
  KEY idx_approval_record_action (action),
  KEY idx_approval_record_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Approval record';

CREATE TABLE IF NOT EXISTS ai_advice_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  task_id BIGINT UNSIGNED NULL,
  node_key VARCHAR(128) NULL,
  advice_type ENUM('pass','verify','reject','risk') NOT NULL,
  advice_content LONGTEXT NULL,
  risk_points JSON NULL,
  confidence DECIMAL(5,4) NULL,
  model_name VARCHAR(128) NULL,
  model_version VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_advice_record_instance_id (instance_id),
  KEY idx_ai_advice_record_task_id (task_id),
  KEY idx_ai_advice_record_advice_type (advice_type),
  KEY idx_ai_advice_record_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI advice record';

CREATE TABLE IF NOT EXISTS ai_correction_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  advice_id BIGINT UNSIGNED NOT NULL,
  instance_id BIGINT UNSIGNED NOT NULL,
  task_id BIGINT UNSIGNED NULL,
  original_advice_type VARCHAR(32) NULL,
  corrected_action VARCHAR(64) NOT NULL,
  correction_reason VARCHAR(1024) NULL,
  corrected_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_correction_log_advice_id (advice_id),
  KEY idx_ai_correction_log_instance_id (instance_id),
  KEY idx_ai_correction_log_task_id (task_id),
  KEY idx_ai_correction_log_corrected_by (corrected_by),
  KEY idx_ai_correction_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI correction log';

CREATE TABLE IF NOT EXISTS approver_resolution_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  task_id BIGINT UNSIGNED NULL,
  node_key VARCHAR(128) NOT NULL,
  approver_type VARCHAR(64) NULL,
  approver_config JSON NULL,
  resolved_user_ids JSON NULL,
  resolution_status VARCHAR(64) NOT NULL,
  error_message TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_approver_resolution_instance_id (instance_id),
  KEY idx_approver_resolution_task_id (task_id),
  KEY idx_approver_resolution_status (resolution_status),
  KEY idx_approver_resolution_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Approver resolution log';

CREATE TABLE IF NOT EXISTS bottleneck_prediction (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  node_key VARCHAR(128) NULL,
  prediction_level ENUM('high_prob_timeout','possible_timeout','none') NOT NULL DEFAULT 'none',
  prediction_reason VARCHAR(1024) NULL,
  predicted_delay_hours DECIMAL(8,2) NULL,
  model_name VARCHAR(128) NULL,
  model_version VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_bottleneck_prediction_instance_id (instance_id),
  KEY idx_bottleneck_prediction_level (prediction_level),
  KEY idx_bottleneck_prediction_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bottleneck prediction';

CREATE TABLE IF NOT EXISTS ai_model_metric (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  metric_date DATE NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  model_version VARCHAR(64) NULL,
  scenario VARCHAR(128) NOT NULL,
  total_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  accepted_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  corrected_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  accuracy DECIMAL(5,4) NULL,
  avg_confidence DECIMAL(5,4) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_model_metric_day_model_scenario (metric_date, model_name, model_version, scenario),
  KEY idx_ai_model_metric_metric_date (metric_date),
  KEY idx_ai_model_metric_model (model_name, model_version),
  KEY idx_ai_model_metric_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI model metric';

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id BIGINT UNSIGNED NULL,
  operation_type ENUM('login','logout','create','update','delete','approve','reject','publish','config_change') NOT NULL,
  target_type ENUM('template','instance','user','role','config','form','fragment','market') NULL,
  target_id BIGINT UNSIGNED NULL,
  operation_content TEXT NULL,
  request_ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_operation_log_operator_id (operator_id),
  KEY idx_operation_log_operation_type (operation_type),
  KEY idx_operation_log_target (target_type, target_id),
  KEY idx_operation_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Operation log';

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_key VARCHAR(128) NOT NULL,
  config_name VARCHAR(128) NOT NULL,
  config_value LONGTEXT NULL,
  value_type ENUM('string','int','float','bool','json') NOT NULL DEFAULT 'string',
  description VARCHAR(512) NULL,
  editable TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_config_key (config_key),
  KEY idx_system_config_deleted (deleted),
  KEY idx_system_config_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System config';

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  receiver_id BIGINT UNSIGNED NOT NULL,
  type ENUM('task_remind','timeout_warning','approval_result','system_notice') NOT NULL,
  title VARCHAR(256) NOT NULL,
  content TEXT NULL,
  target_type VARCHAR(64) NULL,
  target_id BIGINT UNSIGNED NULL,
  read_status TINYINT NOT NULL DEFAULT 0,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_notification_receiver_id (receiver_id),
  KEY idx_notification_type (type),
  KEY idx_notification_read_status (read_status),
  KEY idx_notification_target (target_type, target_id),
  KEY idx_notification_deleted (deleted),
  KEY idx_notification_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Notification';

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT NULL, 'hr_admin', '人事行政类', '人事、行政相关流程分类', 10, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'hr_admin');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT NULL, 'finance', '财务类', '财务相关流程分类', 20, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'finance');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT NULL, 'logistics', '后勤类', '后勤保障相关流程分类', 30, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'logistics');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT NULL, 'management', '管理类', '经营管理相关流程分类', 40, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'management');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'hr_admin' LIMIT 1), 'leave', '请假', '员工请假流程', 11, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'leave');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'hr_admin' LIMIT 1), 'business_trip', '出差', '员工出差流程', 12, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'business_trip');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'finance' LIMIT 1), 'reimbursement', '报销', '费用报销流程', 21, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'reimbursement');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'logistics' LIMIT 1), 'purchase', '采购', '采购申请流程', 31, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'purchase');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled, deleted)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'management' LIMIT 1), 'contract_approval', '合同审批', '合同审批流程', 41, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'contract_approval');

-- Compatibility migration for databases that were initialized by the old sql/init.sql.
SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'department_id') = 0,
  'ALTER TABLE sys_user ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'supervisor_id') = 0,
  'ALTER TABLE sys_user ADD COLUMN supervisor_id BIGINT UNSIGNED NULL AFTER department_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password_hash') = 0,
  'ALTER TABLE sys_user ADD COLUMN password_hash VARCHAR(255) NULL AFTER password',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'real_name') = 0,
  'ALTER TABLE sys_user ADD COLUMN real_name VARCHAR(64) NULL AFTER nickname',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'phone') = 0,
  'ALTER TABLE sys_user ADD COLUMN phone VARCHAR(32) NULL AFTER real_name',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'email') = 0,
  'ALTER TABLE sys_user ADD COLUMN email VARCHAR(128) NULL AFTER phone',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_url') = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(512) NULL AFTER email',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'system_role') = 0,
  'ALTER TABLE sys_user ADD COLUMN system_role ENUM(''super_admin'',''biz_admin'',''normal_user'') NOT NULL DEFAULT ''normal_user'' AFTER role',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'managed_biz_type_ids') = 0,
  'ALTER TABLE sys_user ADD COLUMN managed_biz_type_ids JSON NULL AFTER system_role',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'status') = 0,
  'ALTER TABLE sys_user ADD COLUMN status TINYINT NOT NULL DEFAULT 1 AFTER enabled',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'last_login_at') = 0,
  'ALTER TABLE sys_user ADD COLUMN last_login_at DATETIME NULL AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'created_at') = 0,
  'ALTER TABLE sys_user ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER updated_time',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'updated_at') = 0,
  'ALTER TABLE sys_user ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE sys_user ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER updated_at',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

