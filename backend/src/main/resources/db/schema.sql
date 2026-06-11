sys_userCREATE DATABASE IF NOT EXISTS ai_workflow_mvp
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_workflow_mvp;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 组织部门表
CREATE TABLE IF NOT EXISTS department (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id BIGINT UNSIGNED NULL COMMENT '父部门ID',
  dept_code VARCHAR(64) NOT NULL COMMENT '部门编码',
  dept_name VARCHAR(128) NOT NULL COMMENT '部门名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  leader_user_id BIGINT UNSIGNED NULL COMMENT '部门负责人用户ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-停用,1-正常',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_dept_code (dept_code),
  KEY idx_department_parent_id (parent_id),
  KEY idx_department_leader_user_id (leader_user_id),
  KEY idx_department_status (status),
  KEY idx_department_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';

-- 系统用户表，兼容当前MVP登录代码，同时预留后续JPA字段
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  department_id BIGINT UNSIGNED NULL COMMENT '所属部门ID',
  username VARCHAR(64) NOT NULL COMMENT '用户名',
  password VARCHAR(255) NOT NULL COMMENT '当前MVP使用的BCrypt密码',
  password_hash VARCHAR(255) NULL COMMENT '后续JPA版本预留密码哈希',
  nickname VARCHAR(64) NULL COMMENT '当前MVP使用的昵称',
  real_name VARCHAR(64) NULL COMMENT '真实姓名',
  phone VARCHAR(32) NULL COMMENT '手机号',
  email VARCHAR(128) NULL COMMENT '邮箱',
  avatar_url VARCHAR(512) NULL COMMENT '头像地址',
  role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '当前MVP角色：ADMIN/MANAGER/USER',
  system_role ENUM('super_admin','biz_admin','normal_user') NOT NULL DEFAULT 'normal_user' COMMENT '系统角色',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '当前MVP是否启用：1-启用,0-禁用',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-停用,1-正常,2-锁定',
  last_login_at DATETIME NULL COMMENT '最后登录时间',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当前MVP创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '当前MVP更新时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_department_id (department_id),
  KEY idx_sys_user_role (role),
  KEY idx_sys_user_system_role (system_role),
  KEY idx_sys_user_enabled (enabled),
  KEY idx_sys_user_status (status),
  KEY idx_sys_user_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 业务类型字典表
CREATE TABLE IF NOT EXISTS biz_type_dict (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id BIGINT UNSIGNED NULL COMMENT '父级业务类型ID',
  type_code VARCHAR(64) NOT NULL COMMENT '业务类型编码',
  type_name VARCHAR(128) NOT NULL COMMENT '业务类型名称',
  description VARCHAR(512) NULL COMMENT '描述',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0-停用,1-启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_type_dict_type_code (type_code),
  KEY idx_biz_type_dict_parent_id (parent_id),
  KEY idx_biz_type_dict_enabled (enabled),
  KEY idx_biz_type_dict_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务类型字典表';

-- 表单定义表
CREATE TABLE IF NOT EXISTS form_definition (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  form_code VARCHAR(64) NOT NULL COMMENT '表单编码',
  form_name VARCHAR(128) NOT NULL COMMENT '表单名称',
  biz_type_id BIGINT UNSIGNED NULL COMMENT '业务类型ID',
  version INT NOT NULL DEFAULT 1 COMMENT '版本号',
  status ENUM('draft','published','disabled') NOT NULL DEFAULT 'draft' COMMENT '状态',
  field_list JSON NULL COMMENT '字段列表',
  form_schema JSON NULL COMMENT '表单结构',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人ID',
  published_at DATETIME NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_definition_code_version (form_code, version),
  KEY idx_form_definition_form_code (form_code),
  KEY idx_form_definition_biz_type_id (biz_type_id),
  KEY idx_form_definition_status (status),
  KEY idx_form_definition_created_by (created_by),
  KEY idx_form_definition_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单定义表';

-- 流程模板表
CREATE TABLE IF NOT EXISTS process_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
  template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
  biz_type_id BIGINT UNSIGNED NULL COMMENT '业务类型ID',
  form_id BIGINT UNSIGNED NULL COMMENT '关联表单ID',
  version INT NOT NULL DEFAULT 1 COMMENT '版本号',
  status ENUM('draft','reviewing','published','disabled') NOT NULL DEFAULT 'draft' COMMENT '状态',
  source_type ENUM('ai_generated','manual','market_copy','fragment_combo') NOT NULL DEFAULT 'manual' COMMENT '来源类型',
  bpmn_xml LONGTEXT NULL COMMENT 'BPMN XML',
  node_config JSON NULL COMMENT '节点配置',
  form_bind_config JSON NULL COMMENT '表单绑定配置',
  flowable_deployment_id VARCHAR(128) NULL COMMENT 'Flowable部署ID',
  flowable_process_definition_id VARCHAR(128) NULL COMMENT 'Flowable流程定义ID',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人ID',
  published_at DATETIME NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_template_code_version (template_code, version),
  KEY idx_process_template_template_code (template_code),
  KEY idx_process_template_biz_type_id (biz_type_id),
  KEY idx_process_template_form_id (form_id),
  KEY idx_process_template_status (status),
  KEY idx_process_template_flowable_definition_id (flowable_process_definition_id),
  KEY idx_process_template_created_by (created_by),
  KEY idx_process_template_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程模板表';

-- MVP流程模板表，兼容当前模板CRUD接口
CREATE TABLE IF NOT EXISTS workflow_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_name VARCHAR(128) NOT NULL COMMENT '流程模板名称',
  business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  form_json LONGTEXT NOT NULL COMMENT '表单设计JSON',
  bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN XML',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当前MVP创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '当前MVP更新时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  KEY idx_workflow_template_business_type (business_type),
  KEY idx_workflow_template_status (status),
  KEY idx_workflow_template_created_by (created_by),
  KEY idx_workflow_template_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MVP流程模板表';

-- 流程片段表
CREATE TABLE IF NOT EXISTS process_fragment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  fragment_code VARCHAR(64) NOT NULL COMMENT '片段编码',
  fragment_name VARCHAR(128) NOT NULL COMMENT '片段名称',
  biz_type_id BIGINT UNSIGNED NULL COMMENT '业务类型ID',
  description VARCHAR(1024) NULL COMMENT '描述',
  fragment_type VARCHAR(64) NOT NULL COMMENT '片段类型',
  status ENUM('draft','published','disabled') NOT NULL DEFAULT 'draft' COMMENT '状态',
  bpmn_xml LONGTEXT NULL COMMENT 'BPMN XML',
  node_config JSON NULL COMMENT '节点配置',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人ID',
  published_at DATETIME NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_fragment_fragment_code (fragment_code),
  KEY idx_process_fragment_biz_type_id (biz_type_id),
  KEY idx_process_fragment_fragment_type (fragment_type),
  KEY idx_process_fragment_status (status),
  KEY idx_process_fragment_created_by (created_by),
  KEY idx_process_fragment_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程片段表';

-- 模板与流程片段引用关系表
CREATE TABLE IF NOT EXISTS template_fragment_ref (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL COMMENT '流程模板ID',
  fragment_id BIGINT UNSIGNED NOT NULL COMMENT '流程片段ID',
  fragment_version INT NULL COMMENT '引用片段版本',
  sync_status ENUM('synced','pending_update','unbound') NOT NULL DEFAULT 'synced' COMMENT '同步状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_fragment_ref (template_id, fragment_id),
  KEY idx_template_fragment_ref_template_id (template_id),
  KEY idx_template_fragment_ref_fragment_id (fragment_id),
  KEY idx_template_fragment_ref_sync_status (sync_status),
  KEY idx_template_fragment_ref_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板与流程片段引用关系表';

-- 模板市场表
CREATE TABLE IF NOT EXISTS template_market (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  source_id BIGINT UNSIGNED NOT NULL COMMENT '来源模板或片段ID',
  type ENUM('template','fragment') NOT NULL COMMENT '市场资源类型',
  title VARCHAR(128) NOT NULL COMMENT '标题',
  description VARCHAR(1024) NULL COMMENT '描述',
  cover_url VARCHAR(512) NULL COMMENT '封面地址',
  biz_type_id BIGINT UNSIGNED NULL COMMENT '业务类型ID',
  publisher_id BIGINT UNSIGNED NULL COMMENT '发布人ID',
  use_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用次数',
  rating DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '评分',
  tags JSON NULL COMMENT '标签',
  published_at DATETIME NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  KEY idx_template_market_source (type, source_id),
  KEY idx_template_market_biz_type_id (biz_type_id),
  KEY idx_template_market_publisher_id (publisher_id),
  KEY idx_template_market_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板市场表';

-- 流程节点配置表
CREATE TABLE IF NOT EXISTS flow_node (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL COMMENT '流程模板ID',
  node_key VARCHAR(128) NOT NULL COMMENT 'BPMN节点Key',
  node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
  node_type ENUM('start_event','end_event','user_task','exclusive_gateway','parallel_gateway','inclusive_gateway','sub_process','cc_node') NOT NULL COMMENT '节点类型',
  approver_type VARCHAR(64) NULL COMMENT '审批人类型',
  approver_config JSON NULL COMMENT '审批人配置',
  form_permission JSON NULL COMMENT '表单权限',
  node_config JSON NULL COMMENT '节点业务配置',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_node_template_node_key (template_id, node_key),
  KEY idx_flow_node_template_id (template_id),
  KEY idx_flow_node_node_type (node_type),
  KEY idx_flow_node_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点配置表';

-- 流程实例业务表
CREATE TABLE IF NOT EXISTS process_instance (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_code VARCHAR(64) NOT NULL COMMENT '实例编码',
  template_id BIGINT UNSIGNED NOT NULL COMMENT '流程模板ID',
  form_id BIGINT UNSIGNED NULL COMMENT '表单ID',
  applicant_id BIGINT UNSIGNED NOT NULL COMMENT '申请人ID',
  biz_type_id BIGINT UNSIGNED NULL COMMENT '业务类型ID',
  title VARCHAR(256) NOT NULL COMMENT '流程标题',
  status ENUM('running','pending_modify','completed','rejected','cancelled') NOT NULL DEFAULT 'running' COMMENT '状态',
  form_data JSON NULL COMMENT '表单数据',
  current_node_key VARCHAR(128) NULL COMMENT '当前节点Key',
  flowable_process_instance_id VARCHAR(128) NULL COMMENT 'Flowable流程实例ID',
  started_at DATETIME NULL COMMENT '开始时间',
  ended_at DATETIME NULL COMMENT '结束时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_instance_code (instance_code),
  KEY idx_process_instance_template_id (template_id),
  KEY idx_process_instance_form_id (form_id),
  KEY idx_process_instance_applicant_id (applicant_id),
  KEY idx_process_instance_biz_type_id (biz_type_id),
  KEY idx_process_instance_status (status),
  KEY idx_process_instance_flowable_instance_id (flowable_process_instance_id),
  KEY idx_process_instance_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例业务表';

-- 业务待办任务表
CREATE TABLE IF NOT EXISTS task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  template_id BIGINT UNSIGNED NOT NULL COMMENT '流程模板ID',
  node_key VARCHAR(128) NOT NULL COMMENT '节点Key',
  node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
  assignee_id BIGINT UNSIGNED NULL COMMENT '办理人ID',
  candidate_type VARCHAR(64) NULL COMMENT '候选人类型',
  candidate_config JSON NULL COMMENT '候选人配置',
  status ENUM('pending','processing','completed','delegated','timeout') NOT NULL DEFAULT 'pending' COMMENT '任务状态',
  flowable_task_id VARCHAR(128) NULL COMMENT 'Flowable任务ID',
  due_time DATETIME NULL COMMENT '截止时间',
  claimed_at DATETIME NULL COMMENT '签收时间',
  completed_at DATETIME NULL COMMENT '完成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  KEY idx_task_instance_id (instance_id),
  KEY idx_task_template_id (template_id),
  KEY idx_task_assignee_id (assignee_id),
  KEY idx_task_status (status),
  KEY idx_task_flowable_task_id (flowable_task_id),
  KEY idx_task_due_time (due_time),
  KEY idx_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务待办任务表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS approval_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  task_id BIGINT UNSIGNED NULL COMMENT '业务任务ID',
  node_key VARCHAR(128) NOT NULL COMMENT '节点Key',
  approver_id BIGINT UNSIGNED NOT NULL COMMENT '审批人ID',
  action ENUM('approve','reject','supplement','delegate','transfer') NOT NULL COMMENT '审批动作',
  comment_text TEXT NULL COMMENT '审批意见',
  attachment_list JSON NULL COMMENT '附件列表',
  operated_at DATETIME NOT NULL COMMENT '操作时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_approval_record_instance_id (instance_id),
  KEY idx_approval_record_task_id (task_id),
  KEY idx_approval_record_approver_id (approver_id),
  KEY idx_approval_record_action (action),
  KEY idx_approval_record_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表';

-- AI审批建议记录表
CREATE TABLE IF NOT EXISTS ai_advice_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  task_id BIGINT UNSIGNED NULL COMMENT '业务任务ID',
  node_key VARCHAR(128) NULL COMMENT '节点Key',
  advice_type ENUM('pass','verify','reject','risk') NOT NULL COMMENT '建议类型',
  advice_content LONGTEXT NULL COMMENT '建议内容',
  risk_points JSON NULL COMMENT '风险点',
  confidence DECIMAL(5,4) NULL COMMENT '置信度',
  model_name VARCHAR(128) NULL COMMENT '模型名称',
  model_version VARCHAR(64) NULL COMMENT '模型版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_advice_record_instance_id (instance_id),
  KEY idx_ai_advice_record_task_id (task_id),
  KEY idx_ai_advice_record_advice_type (advice_type),
  KEY idx_ai_advice_record_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI审批建议记录表';

-- AI建议纠错记录表
CREATE TABLE IF NOT EXISTS ai_correction_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  advice_id BIGINT UNSIGNED NOT NULL COMMENT 'AI建议ID',
  instance_id BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  task_id BIGINT UNSIGNED NULL COMMENT '业务任务ID',
  original_advice_type VARCHAR(32) NULL COMMENT '原AI建议类型',
  corrected_action VARCHAR(64) NOT NULL COMMENT '人工修正动作',
  correction_reason VARCHAR(1024) NULL COMMENT '修正原因',
  corrected_by BIGINT UNSIGNED NULL COMMENT '修正人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_correction_log_advice_id (advice_id),
  KEY idx_ai_correction_log_instance_id (instance_id),
  KEY idx_ai_correction_log_task_id (task_id),
  KEY idx_ai_correction_log_corrected_by (corrected_by),
  KEY idx_ai_correction_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI建议纠错记录表';

-- 审批人解析日志表
CREATE TABLE IF NOT EXISTS approver_resolution_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  task_id BIGINT UNSIGNED NULL COMMENT '业务任务ID',
  node_key VARCHAR(128) NOT NULL COMMENT '节点Key',
  approver_type VARCHAR(64) NULL COMMENT '审批人类型',
  approver_config JSON NULL COMMENT '审批人配置',
  resolved_user_ids JSON NULL COMMENT '解析出的用户ID列表',
  resolution_status VARCHAR(64) NOT NULL COMMENT '解析状态',
  error_message TEXT NULL COMMENT '错误信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_approver_resolution_instance_id (instance_id),
  KEY idx_approver_resolution_task_id (task_id),
  KEY idx_approver_resolution_status (resolution_status),
  KEY idx_approver_resolution_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批人解析日志表';

-- 流程瓶颈预测表
CREATE TABLE IF NOT EXISTS bottleneck_prediction (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  node_key VARCHAR(128) NULL COMMENT '节点Key',
  prediction_level ENUM('high_prob_timeout','possible_timeout','none') NOT NULL DEFAULT 'none' COMMENT '预测级别',
  prediction_reason VARCHAR(1024) NULL COMMENT '预测原因',
  predicted_delay_hours DECIMAL(8,2) NULL COMMENT '预计延迟小时数',
  model_name VARCHAR(128) NULL COMMENT '模型名称',
  model_version VARCHAR(64) NULL COMMENT '模型版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_bottleneck_prediction_instance_id (instance_id),
  KEY idx_bottleneck_prediction_level (prediction_level),
  KEY idx_bottleneck_prediction_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程瓶颈预测表';

-- AI模型效果指标表
CREATE TABLE IF NOT EXISTS ai_model_metric (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  metric_date DATE NOT NULL COMMENT '指标日期',
  model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
  model_version VARCHAR(64) NULL COMMENT '模型版本',
  scenario VARCHAR(128) NOT NULL COMMENT '业务场景',
  total_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总次数',
  accepted_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '采纳次数',
  corrected_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '纠错次数',
  accuracy DECIMAL(5,4) NULL COMMENT '准确率',
  avg_confidence DECIMAL(5,4) NULL COMMENT '平均置信度',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_model_metric_day_model_scenario (metric_date, model_name, model_version, scenario),
  KEY idx_ai_model_metric_metric_date (metric_date),
  KEY idx_ai_model_metric_model (model_name, model_version),
  KEY idx_ai_model_metric_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型效果指标表';

-- 系统操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id BIGINT UNSIGNED NULL COMMENT '操作人ID',
  operation_type ENUM('login','logout','create','update','delete','approve','reject','publish','config_change') NOT NULL COMMENT '操作类型',
  target_type ENUM('template','instance','user','role','config','form','fragment','market') NULL COMMENT '目标类型',
  target_id BIGINT UNSIGNED NULL COMMENT '目标ID',
  operation_content TEXT NULL COMMENT '操作内容',
  request_ip VARCHAR(64) NULL COMMENT '请求IP',
  user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_operation_log_operator_id (operator_id),
  KEY idx_operation_log_operation_type (operation_type),
  KEY idx_operation_log_target (target_type, target_id),
  KEY idx_operation_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_key VARCHAR(128) NOT NULL COMMENT '配置键',
  config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
  config_value LONGTEXT NULL COMMENT '配置值',
  value_type ENUM('string','int','float','bool','json') NOT NULL DEFAULT 'string' COMMENT '值类型',
  description VARCHAR(512) NULL COMMENT '描述',
  editable TINYINT NOT NULL DEFAULT 1 COMMENT '0-不可编辑,1-可编辑',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_config_key (config_key),
  KEY idx_system_config_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 系统通知表
CREATE TABLE IF NOT EXISTS notification (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  receiver_id BIGINT UNSIGNED NOT NULL COMMENT '接收人ID',
  type ENUM('task_remind','timeout_warning','approval_result','system_notice') NOT NULL COMMENT '通知类型',
  title VARCHAR(256) NOT NULL COMMENT '通知标题',
  content TEXT NULL COMMENT '通知内容',
  target_type VARCHAR(64) NULL COMMENT '目标类型',
  target_id BIGINT UNSIGNED NULL COMMENT '目标ID',
  read_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-未读,1-已读',
  read_at DATETIME NULL COMMENT '阅读时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0-未删除,1-已删除',
  PRIMARY KEY (id),
  KEY idx_notification_receiver_id (receiver_id),
  KEY idx_notification_type (type),
  KEY idx_notification_read_status (read_status),
  KEY idx_notification_target (target_type, target_id),
  KEY idx_notification_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT NULL, 'hr_admin', '人事行政类', '人事、行政相关流程分类', 10, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'hr_admin');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT NULL, 'finance', '财务类', '财务相关流程分类', 20, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'finance');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT NULL, 'logistics', '后勤类', '后勤保障相关流程分类', 30, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'logistics');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT NULL, 'management', '管理类', '经营管理相关流程分类', 40, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'management');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'hr_admin' LIMIT 1), 'leave', '请假', '员工请假流程', 11, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'leave');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'hr_admin' LIMIT 1), 'business_trip', '出差', '员工出差流程', 12, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'business_trip');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'finance' LIMIT 1), 'reimbursement', '报销', '费用报销流程', 21, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'reimbursement');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'logistics' LIMIT 1), 'purchase', '采购', '采购申请流程', 31, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'purchase');

INSERT INTO biz_type_dict (parent_id, type_code, type_name, description, sort_order, enabled)
SELECT (SELECT id FROM biz_type_dict WHERE type_code = 'management' LIMIT 1), 'contract_approval', '合同审批', '合同审批流程', 41, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_type_dict WHERE type_code = 'contract_approval');

SET FOREIGN_KEY_CHECKS = 1;
