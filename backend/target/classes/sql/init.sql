CREATE DATABASE IF NOT EXISTS ai_workflow_mvp
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_workflow_mvp;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
  password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  role VARCHAR(32) NOT NULL COMMENT '角色：ADMIN/MANAGER/USER',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_sys_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS workflow_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  template_name VARCHAR(128) NOT NULL COMMENT '流程模板名称',
  business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  form_json LONGTEXT NOT NULL COMMENT '表单设计JSON',
  bpmn_xml LONGTEXT NOT NULL COMMENT 'BPMN XML',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_workflow_template_business_type (business_type),
  KEY idx_workflow_template_status (status),
  KEY idx_workflow_template_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板表';

-- 首次启动时后端也会自动创建默认账号；如需手工初始化，可使用 BCrypt 密码后再插入。
-- 默认账号：admin / admin123
