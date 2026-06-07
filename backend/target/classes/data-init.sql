-- 初始化数据：表单定义和流程模板
-- 执行前请确保数据库表已创建

-- 1. 插入业务类型
INSERT INTO biz_type_dict (type_code, type_name, description, parent_id, sort_order, enabled, deleted, created_at, updated_at) VALUES
('HR', '人事流程', '人事相关审批流程', NULL, 1, 1, 0, NOW(), NOW()),
('FINANCE', '财务流程', '财务相关审批流程', NULL, 2, 1, 0, NOW(), NOW()),
('ADMIN', '行政流程', '行政相关审批流程', NULL, 3, 1, 0, NOW(), NOW()),
('IT', 'IT流程', 'IT相关审批流程', NULL, 4, 1, 0, NOW(), NOW());

-- 2. 插入表单定义
-- 请假申请表单
INSERT INTO form_definition (form_code, form_name, description, form_schema, field_list, status, version, deleted, created_at, updated_at) VALUES
('FORM_LEAVE', '请假申请表', '员工请假申请表单',
'{"title": "请假申请表", "description": "填写请假信息"}',
'[
  {"fieldKey": "leaveType", "fieldName": "请假类型", "fieldType": "select", "required": true, "options": ["事假", "病假", "年假", "婚假", "产假"], "placeholder": "请选择请假类型"},
  {"fieldKey": "startDate", "fieldName": "开始日期", "fieldType": "date", "required": true, "placeholder": "请选择开始日期"},
  {"fieldKey": "endDate", "fieldName": "结束日期", "fieldType": "date", "required": true, "placeholder": "请选择结束日期"},
  {"fieldKey": "leaveDays", "fieldName": "请假天数", "fieldType": "number", "required": true, "placeholder": "自动计算或手动输入"},
  {"fieldKey": "reason", "fieldName": "请假原因", "fieldType": "textarea", "required": true, "placeholder": "请输入请假原因"}
]',
'published', 1, 0, NOW(), NOW());

-- 报销申请表单
INSERT INTO form_definition (form_code, form_name, description, form_schema, field_list, status, version, deleted, created_at, updated_at) VALUES
('FORM_EXPENSE', '费用报销表', '费用报销申请表单',
'{"title": "费用报销表", "description": "填写报销信息"}',
'[
  {"fieldKey": "expenseType", "fieldName": "报销类型", "fieldType": "select", "required": true, "options": ["差旅费", "交通费", "餐饮费", "办公用品", "其他"], "placeholder": "请选择报销类型"},
  {"fieldKey": "amount", "fieldName": "报销金额", "fieldType": "number", "required": true, "placeholder": "请输入金额"},
  {"fieldKey": "expenseDate", "fieldName": "消费日期", "fieldType": "date", "required": true, "placeholder": "请选择消费日期"},
  {"fieldKey": "description", "fieldName": "费用说明", "fieldType": "textarea", "required": true, "placeholder": "请输入费用说明"},
  {"fieldKey": "attachments", "fieldName": "附件", "fieldType": "file", "required": false, "placeholder": "上传发票等凭证"}
]',
'published', 1, 0, NOW(), NOW());

-- 设备报修表单
INSERT INTO form_definition (form_code, form_name, description, form_schema, field_list, status, version, deleted, created_at, updated_at) VALUES
('FORM_REPAIR', '设备报修表', '设备报修申请表单',
'{"title": "设备报修表", "description": "填写报修信息"}',
'[
  {"fieldKey": "deviceName", "fieldName": "设备名称", "fieldType": "text", "required": true, "placeholder": "请输入设备名称"},
  {"fieldKey": "deviceLocation", "fieldName": "设备位置", "fieldType": "text", "required": true, "placeholder": "请输入设备所在位置"},
  {"fieldKey": "faultType", "fieldName": "故障类型", "fieldType": "select", "required": true, "options": ["硬件故障", "软件故障", "网络故障", "其他"], "placeholder": "请选择故障类型"},
  {"fieldKey": "faultDescription", "fieldName": "故障描述", "fieldType": "textarea", "required": true, "placeholder": "请描述故障情况"},
  {"fieldKey": "urgency", "fieldName": "紧急程度", "fieldType": "select", "required": true, "options": ["紧急", "一般", "不紧急"], "placeholder": "请选择紧急程度"}
]',
'published', 1, 0, NOW(), NOW());

-- 出差申请表单
INSERT INTO form_definition (form_code, form_name, description, form_schema, field_list, status, version, deleted, created_at, updated_at) VALUES
('FORM_TRAVEL', '出差申请表', '出差申请表单',
'{"title": "出差申请表", "description": "填写出差信息"}',
'[
  {"fieldKey": "destination", "fieldName": "目的地", "fieldType": "text", "required": true, "placeholder": "请输入出差目的地"},
  {"fieldKey": "startDate", "fieldName": "出发日期", "fieldType": "date", "required": true, "placeholder": "请选择出发日期"},
  {"fieldKey": "endDate", "fieldName": "返回日期", "fieldType": "date", "required": true, "placeholder": "请选择返回日期"},
  {"fieldKey": "travelDays", "fieldName": "出差天数", "fieldType": "number", "required": true, "placeholder": "出差天数"},
  {"fieldKey": "reason", "fieldName": "出差事由", "fieldType": "textarea", "required": true, "placeholder": "请输入出差事由"},
  {"fieldKey": "budget", "fieldName": "预估费用", "fieldType": "number", "required": false, "placeholder": "预估出差费用"}
]',
'published', 1, 0, NOW(), NOW());

-- 3. 插入流程模板（BPMN XML）
-- 请假审批流程
INSERT INTO process_template (template_code, template_name, description, biz_type_id, form_id, bpmn_xml, node_config, status, version, deleted, created_at, updated_at) VALUES
('TPL_LEAVE', '请假审批流程', '员工请假审批流程，需要部门主管审批',
(SELECT id FROM biz_type_dict WHERE type_code = 'HR'),
(SELECT id FROM form_definition WHERE form_code = 'FORM_LEAVE'),
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://aiflow.com/process">
  <process id="leave_approval" name="请假审批流程" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="dept_approve" name="部门主管审批" flowable:assignee="${applicantId}"/>
    <userTask id="hr_approve" name="人事审批" flowable:candidateGroups="hr"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="dept_approve"/>
    <sequenceFlow id="flow2" sourceRef="dept_approve" targetRef="hr_approve"/>
    <sequenceFlow id="flow3" sourceRef="hr_approve" targetRef="end"/>
  </process>
</definitions>',
'{"nodes": [{"id": "start", "name": "开始", "type": "startEvent"}, {"id": "dept_approve", "name": "部门主管审批", "type": "userTask", "assigneeType": "applicant"}, {"id": "hr_approve", "name": "人事审批", "type": "userTask", "assigneeType": "role", "assigneeRole": "hr"}, {"id": "end", "name": "结束", "type": "endEvent"}]}',
'draft', 1, 0, NOW(), NOW());

-- 费用报销流程
INSERT INTO process_template (template_code, template_name, description, biz_type_id, form_id, bpmn_xml, node_config, status, version, deleted, created_at, updated_at) VALUES
('TPL_EXPENSE', '费用报销流程', '费用报销审批流程',
(SELECT id FROM biz_type_dict WHERE type_code = 'FINANCE'),
(SELECT id FROM form_definition WHERE form_code = 'FORM_EXPENSE'),
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://aiflow.com/process">
  <process id="expense_approval" name="费用报销流程" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="dept_approve" name="部门审批" flowable:assignee="${applicantId}"/>
    <userTask id="finance_approve" name="财务审批" flowable:candidateGroups="finance"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="dept_approve"/>
    <sequenceFlow id="flow2" sourceRef="dept_approve" targetRef="finance_approve"/>
    <sequenceFlow id="flow3" sourceRef="finance_approve" targetRef="end"/>
  </process>
</definitions>',
'{"nodes": [{"id": "start", "name": "开始", "type": "startEvent"}, {"id": "dept_approve", "name": "部门审批", "type": "userTask", "assigneeType": "applicant"}, {"id": "finance_approve", "name": "财务审批", "type": "userTask", "assigneeType": "role", "assigneeRole": "finance"}, {"id": "end", "name": "结束", "type": "endEvent"}]}',
'draft', 1, 0, NOW(), NOW());

-- 设备报修流程
INSERT INTO process_template (template_code, template_name, description, biz_type_id, form_id, bpmn_xml, node_config, status, version, deleted, created_at, updated_at) VALUES
('TPL_REPAIR', '设备报修流程', '设备报修处理流程',
(SELECT id FROM biz_type_dict WHERE type_code = 'ADMIN'),
(SELECT id FROM form_definition WHERE form_code = 'FORM_REPAIR'),
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://aiflow.com/process">
  <process id="repair_process" name="设备报修流程" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="admin_handle" name="行政处理" flowable:candidateGroups="admin"/>
    <userTask id="it_repair" name="IT维修" flowable:candidateGroups="it"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="admin_handle"/>
    <sequenceFlow id="flow2" sourceRef="admin_handle" targetRef="it_repair"/>
    <sequenceFlow id="flow3" sourceRef="it_repair" targetRef="end"/>
  </process>
</definitions>',
'{"nodes": [{"id": "start", "name": "开始", "type": "startEvent"}, {"id": "admin_handle", "name": "行政处理", "type": "userTask", "assigneeType": "role", "assigneeRole": "admin"}, {"id": "it_repair", "name": "IT维修", "type": "userTask", "assigneeType": "role", "assigneeRole": "it"}, {"id": "end", "name": "结束", "type": "endEvent"}]}',
'draft', 1, 0, NOW(), NOW());

-- 出差申请流程
INSERT INTO process_template (template_code, template_name, description, biz_type_id, form_id, bpmn_xml, node_config, status, version, deleted, created_at, updated_at) VALUES
('TPL_TRAVEL', '出差申请流程', '出差申请审批流程',
(SELECT id FROM biz_type_dict WHERE type_code = 'HR'),
(SELECT id FROM form_definition WHERE form_code = 'FORM_TRAVEL'),
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://aiflow.com/process">
  <process id="travel_approval" name="出差申请流程" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="dept_approve" name="部门审批" flowable:assignee="${applicantId}"/>
    <userTask id="boss_approve" name="总经理审批" flowable:candidateGroups="boss"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="dept_approve"/>
    <sequenceFlow id="flow2" sourceRef="dept_approve" targetRef="boss_approve"/>
    <sequenceFlow id="flow3" sourceRef="boss_approve" targetRef="end"/>
  </process>
</definitions>',
'{"nodes": [{"id": "start", "name": "开始", "type": "startEvent"}, {"id": "dept_approve", "name": "部门审批", "type": "userTask", "assigneeType": "applicant"}, {"id": "boss_approve", "name": "总经理审批", "type": "userTask", "assigneeType": "role", "assigneeRole": "boss"}, {"id": "end", "name": "结束", "type": "endEvent"}]}',
'draft', 1, 0, NOW(), NOW());

-- 查询插入结果
SELECT '业务类型' as '类型', COUNT(*) as '数量' FROM biz_type_dict WHERE deleted = 0
UNION ALL
SELECT '表单定义', COUNT(*) FROM form_definition WHERE deleted = 0
UNION ALL
SELECT '流程模板', COUNT(*) FROM process_template WHERE deleted = 0;
