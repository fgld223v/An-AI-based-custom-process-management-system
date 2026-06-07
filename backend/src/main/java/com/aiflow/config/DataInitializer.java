package com.aiflow.config;

import com.aiflow.enums.FormStatus;
import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Order(10)
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final DepartmentRepository departmentRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository sysUserRepository,
                           BizTypeDictRepository bizTypeDictRepository,
                           DepartmentRepository departmentRepository,
                           FormDefinitionRepository formDefinitionRepository,
                           ProcessTemplateRepository processTemplateRepository,
                           PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.bizTypeDictRepository = bizTypeDictRepository;
        this.departmentRepository = departmentRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.processTemplateRepository = processTemplateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initRootDepartment();
        initDefaultAdmin();
        initBizTypes();
        initFormDefinitions();
        initProcessTemplates();
    }

    private Department initRootDepartment() {
        Department existing = departmentRepository.findFirstByDeptCode("root");
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        Department department = Department.builder()
                .parentId(null)
                .deptCode("root")
                .deptName("默认组织")
                .sortOrder(0)
                .status(1)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return departmentRepository.save(department);
    }

    private void initDefaultAdmin() {
        if (sysUserRepository.existsByUsername("admin")) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser admin = SysUser.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .nickname("系统管理员")
                .role("ADMIN")
                .enabled(1)
                .createdTime(now)
                .updatedTime(now)
                .build();
        sysUserRepository.save(admin);
    }

    private void initBizTypes() {
        BizTypeDict hrAdmin = initBizType(null, "hr_admin", "人事行政类", "人事、行政相关流程分类", 10);
        BizTypeDict finance = initBizType(null, "finance", "财务类", "财务相关流程分类", 20);
        BizTypeDict logistics = initBizType(null, "logistics", "后勤类", "后勤保障相关流程分类", 30);
        BizTypeDict management = initBizType(null, "management", "管理类", "经营管理相关流程分类", 40);

        initBizType(hrAdmin.getId(), "leave", "请假", "员工请假流程", 11);
        initBizType(hrAdmin.getId(), "business_trip", "出差", "员工出差流程", 12);
        initBizType(finance.getId(), "reimbursement", "报销", "费用报销流程", 21);
        initBizType(logistics.getId(), "purchase", "采购", "采购申请流程", 31);
        initBizType(management.getId(), "contract_approval", "合同审批", "合同审批流程", 41);
    }

    private BizTypeDict initBizType(Long parentId, String typeCode, String typeName, String description, Integer sortOrder) {
        return bizTypeDictRepository.findByTypeCode(typeCode)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    BizTypeDict item = BizTypeDict.builder()
                            .parentId(parentId)
                            .typeCode(typeCode)
                            .typeName(typeName)
                            .description(description)
                            .sortOrder(sortOrder)
                            .enabled(1)
                            .createdAt(now)
                            .updatedAt(now)
                            .deleted(0)
                            .build();
                    return bizTypeDictRepository.save(item);
                });
    }

    private void initFormDefinitions() {
        initFormDefinition("FORM_LEAVE", "请假申请表", 
                "[{\"fieldKey\":\"leaveType\",\"fieldName\":\"请假类型\",\"fieldType\":\"select\",\"required\":true,\"options\":[\"事假\",\"病假\",\"年假\",\"婚假\",\"产假\"]},{\"fieldKey\":\"startDate\",\"fieldName\":\"开始日期\",\"fieldType\":\"date\",\"required\":true},{\"fieldKey\":\"endDate\",\"fieldName\":\"结束日期\",\"fieldType\":\"date\",\"required\":true},{\"fieldKey\":\"leaveDays\",\"fieldName\":\"请假天数\",\"fieldType\":\"number\",\"required\":true},{\"fieldKey\":\"reason\",\"fieldName\":\"请假原因\",\"fieldType\":\"textarea\",\"required\":true}]");
        
        initFormDefinition("FORM_EXPENSE", "费用报销表",
                "[{\"fieldKey\":\"expenseType\",\"fieldName\":\"报销类型\",\"fieldType\":\"select\",\"required\":true,\"options\":[\"差旅费\",\"交通费\",\"餐饮费\",\"办公用品\",\"其他\"]},{\"fieldKey\":\"amount\",\"fieldName\":\"报销金额\",\"fieldType\":\"number\",\"required\":true},{\"fieldKey\":\"expenseDate\",\"fieldName\":\"消费日期\",\"fieldType\":\"date\",\"required\":true},{\"fieldKey\":\"description\",\"fieldName\":\"费用说明\",\"fieldType\":\"textarea\",\"required\":true}]");
        
        initFormDefinition("FORM_REPAIR", "设备报修表",
                "[{\"fieldKey\":\"deviceName\",\"fieldName\":\"设备名称\",\"fieldType\":\"text\",\"required\":true},{\"fieldKey\":\"deviceLocation\",\"fieldName\":\"设备位置\",\"fieldType\":\"text\",\"required\":true},{\"fieldKey\":\"faultType\",\"fieldName\":\"故障类型\",\"fieldType\":\"select\",\"required\":true,\"options\":[\"硬件故障\",\"软件故障\",\"网络故障\",\"其他\"]},{\"fieldKey\":\"faultDescription\",\"fieldName\":\"故障描述\",\"fieldType\":\"textarea\",\"required\":true}]");
        
        initFormDefinition("FORM_TRAVEL", "出差申请表",
                "[{\"fieldKey\":\"destination\",\"fieldName\":\"目的地\",\"fieldType\":\"text\",\"required\":true},{\"fieldKey\":\"startDate\",\"fieldName\":\"出发日期\",\"fieldType\":\"date\",\"required\":true},{\"fieldKey\":\"endDate\",\"fieldName\":\"返回日期\",\"fieldType\":\"date\",\"required\":true},{\"fieldKey\":\"travelDays\",\"fieldName\":\"出差天数\",\"fieldType\":\"number\",\"required\":true},{\"fieldKey\":\"reason\",\"fieldName\":\"出差事由\",\"fieldType\":\"textarea\",\"required\":true}]");
    }

    private FormDefinition initFormDefinition(String formCode, String formName, String fieldList) {
        return formDefinitionRepository.findByFormCodeAndVersion(formCode, 1)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    FormDefinition form = FormDefinition.builder()
                            .formCode(formCode)
                            .formName(formName)
                            .version(1)
                            .status(FormStatus.PUBLISHED)
                            .fieldList(fieldList)
                            .formSchema("{\"title\":\"" + formName + "\"}")
                            .createdBy(1L)
                            .publishedAt(now)
                            .createdAt(now)
                            .updatedAt(now)
                            .deleted(0)
                            .build();
                    return formDefinitionRepository.save(form);
                });
    }

    private void initProcessTemplates() {
        BizTypeDict hrAdmin = bizTypeDictRepository.findByTypeCode("hr_admin").orElse(null);
        BizTypeDict finance = bizTypeDictRepository.findByTypeCode("finance").orElse(null);
        BizTypeDict logistics = bizTypeDictRepository.findByTypeCode("logistics").orElse(null);
        
        FormDefinition leaveForm = formDefinitionRepository.findByFormCodeAndVersion("FORM_LEAVE", 1).orElse(null);
        FormDefinition expenseForm = formDefinitionRepository.findByFormCodeAndVersion("FORM_EXPENSE", 1).orElse(null);
        FormDefinition repairForm = formDefinitionRepository.findByFormCodeAndVersion("FORM_REPAIR", 1).orElse(null);
        FormDefinition travelForm = formDefinitionRepository.findByFormCodeAndVersion("FORM_TRAVEL", 1).orElse(null);
        
        if (hrAdmin != null && leaveForm != null) {
            initProcessTemplate("TPL_LEAVE", "请假审批流程", hrAdmin.getId(), leaveForm.getId(),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://aiflow.com/process\"><process id=\"leave_approval\" name=\"请假审批流程\" isExecutable=\"true\"><startEvent id=\"start\" name=\"开始\"/><userTask id=\"dept_approve\" name=\"部门主管审批\" flowable:assignee=\"${applicantId}\"/><userTask id=\"hr_approve\" name=\"人事审批\" flowable:candidateGroups=\"hr\"/><endEvent id=\"end\" name=\"结束\"/><sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"dept_approve\"/><sequenceFlow id=\"flow2\" sourceRef=\"dept_approve\" targetRef=\"hr_approve\"/><sequenceFlow id=\"flow3\" sourceRef=\"hr_approve\" targetRef=\"end\"/></process></definitions>");
        }
        
        if (finance != null && expenseForm != null) {
            initProcessTemplate("TPL_EXPENSE", "费用报销流程", finance.getId(), expenseForm.getId(),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://aiflow.com/process\"><process id=\"expense_approval\" name=\"费用报销流程\" isExecutable=\"true\"><startEvent id=\"start\" name=\"开始\"/><userTask id=\"dept_approve\" name=\"部门审批\" flowable:assignee=\"${applicantId}\"/><userTask id=\"finance_approve\" name=\"财务审批\" flowable:candidateGroups=\"finance\"/><endEvent id=\"end\" name=\"结束\"/><sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"dept_approve\"/><sequenceFlow id=\"flow2\" sourceRef=\"dept_approve\" targetRef=\"finance_approve\"/><sequenceFlow id=\"flow3\" sourceRef=\"finance_approve\" targetRef=\"end\"/></process></definitions>");
        }
        
        if (logistics != null && repairForm != null) {
            initProcessTemplate("TPL_REPAIR", "设备报修流程", logistics.getId(), repairForm.getId(),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://aiflow.com/process\"><process id=\"repair_process\" name=\"设备报修流程\" isExecutable=\"true\"><startEvent id=\"start\" name=\"开始\"/><userTask id=\"admin_handle\" name=\"行政处理\" flowable:candidateGroups=\"admin\"/><userTask id=\"it_repair\" name=\"IT维修\" flowable:candidateGroups=\"it\"/><endEvent id=\"end\" name=\"结束\"/><sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"admin_handle\"/><sequenceFlow id=\"flow2\" sourceRef=\"admin_handle\" targetRef=\"it_repair\"/><sequenceFlow id=\"flow3\" sourceRef=\"it_repair\" targetRef=\"end\"/></process></definitions>");
        }
        
        if (hrAdmin != null && travelForm != null) {
            initProcessTemplate("TPL_TRAVEL", "出差申请流程", hrAdmin.getId(), travelForm.getId(),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://aiflow.com/process\"><process id=\"travel_approval\" name=\"出差申请流程\" isExecutable=\"true\"><startEvent id=\"start\" name=\"开始\"/><userTask id=\"dept_approve\" name=\"部门审批\" flowable:assignee=\"${applicantId}\"/><userTask id=\"boss_approve\" name=\"总经理审批\" flowable:candidateGroups=\"boss\"/><endEvent id=\"end\" name=\"结束\"/><sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"dept_approve\"/><sequenceFlow id=\"flow2\" sourceRef=\"dept_approve\" targetRef=\"boss_approve\"/><sequenceFlow id=\"flow3\" sourceRef=\"boss_approve\" targetRef=\"end\"/></process></definitions>");
        }
    }

    private ProcessTemplate initProcessTemplate(String templateCode, String templateName, 
                                                Long bizTypeId, Long formId, String bpmnXml) {
        return processTemplateRepository.findByTemplateCodeAndVersion(templateCode, 1)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    ProcessTemplate template = ProcessTemplate.builder()
                            .templateCode(templateCode)
                            .templateName(templateName)
                            .bizTypeId(bizTypeId)
                            .formId(formId)
                            .version(1)
                            .status(TemplateStatus.PUBLISHED)
                            .sourceType(TemplateSourceType.MANUAL)
                            .bpmnXml(bpmnXml)
                            .createdBy(1L)
                            .publishedAt(now)
                            .createdAt(now)
                            .updatedAt(now)
                            .deleted(0)
                            .build();
                    return processTemplateRepository.save(template);
                });
    }
}
