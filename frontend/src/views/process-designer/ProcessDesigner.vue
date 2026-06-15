<template>
  <div class="process-designer-page">
    <main class="process-workspace">
      <section class="process-header-card">
        <div>
          <el-tag type="success" effect="plain">在线</el-tag>
          <el-tag effect="plain">v1.0</el-tag>
          <el-tag effect="plain">节点 {{ elementCount }}</el-tag>
          <el-tag type="warning" effect="plain">提示 0</el-tag>
          <el-tag effect="plain">{{ zoomPercent }}%</el-tag>
          <h1>流程设计器</h1>
          <p>使用 bpmn.js 原生工具栏绘制流程，也可以点击常用业务节点快速创建。</p>
        </div>
        <div class="process-actions">
          <el-button round :icon="Refresh" @click="loadDefaultDiagram">默认流程</el-button>
          <el-button round :icon="Upload" @click="importVisible = true">回显 XML</el-button>
          <el-button round :icon="Download" @click="exportXml">导出 XML</el-button>
          <el-button round type="success" :icon="Check" @click="saveXml">保存</el-button>
        </div>
      </section>

      <section class="quick-node-card">
        <div class="quick-node-title">
          <strong>常用业务节点</strong>
          <span>用业务语言创建 BPMN 元素，配置会保存在前端状态中</span>
        </div>
        <div class="quick-node-list">
          <el-button
            v-for="node in BUSINESS_NODE_CONFIGS"
            :key="node.businessType"
            round
            :icon="node.icon"
            @click="createBusinessNode(node)"
          >
            {{ node.label }}
          </el-button>
        </div>
      </section>

      <section class="bpmn-shell">
        <div ref="canvasRef" class="bpmn-canvas"></div>
        <div class="minimap-card">
          <div class="minimap-title">Mini map</div>
          <div class="minimap-track">
            <span></span>
            <i></i>
          </div>
        </div>
      </section>
    </main>

    <aside class="process-property-panel">
      <div class="designer-panel-head">
        <span>节点属性</span>
        <small>{{ selectedConfig ? getBusinessLabel(selectedConfig.businessType) : '未选择' }}</small>
      </div>

      <el-empty v-if="!selectedElement || !selectedConfig" description="点击画布节点查看配置" />
      <el-form v-else label-position="top" class="property-form">
        <el-form-item label="节点ID">
          <el-input :model-value="selectedConfig.nodeId" disabled />
        </el-form-item>
        <el-form-item label="BPMN 类型">
          <el-input :model-value="selectedConfig.bpmnType" disabled />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input :model-value="getBusinessLabel(selectedConfig.businessType)" disabled />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="selectedConfig.nodeName" placeholder="请输入节点名称" @change="updateSelectedName" />
        </el-form-item>

        <section v-if="isFormBindableNode(selectedConfig)" class="node-form-section">
          <el-divider content-position="left">表单绑定</el-divider>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="当前仅保存节点表单绑定配置；运行时将优先使用节点表单，否则按配置回退到模板默认表单。"
          />
          <el-form-item label="表单绑定模式">
            <el-select v-model="selectedConfig.formBindingMode" style="width: 100%" @change="handleFormBindingModeChange">
              <el-option label="不使用表单" value="none" />
              <el-option label="使用模板默认表单" value="template_default" />
              <el-option label="绑定节点表单" value="node_form" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedConfig.formBindingMode === 'node_form'" label="绑定表单">
            <el-select
              v-model="selectedConfig.formId"
              clearable
              filterable
              placeholder="请选择已发布表单"
              style="width: 100%"
              :loading="formsLoading"
              @visible-change="handleFormSelectVisibleChange"
              @change="syncNodeConfig"
            >
              <el-option v-for="item in forms" :key="item.id" :label="item.formName" :value="item.id" />
            </el-select>
            <div class="designer-empty-hint">
              <span v-if="forms.length === 0">暂无已发布表单，可先在表单设计器中创建并发布表单。</span>
              <el-button text type="success" :loading="formsLoading" @click="refreshPublishedForms">刷新表单</el-button>
            </div>
          </el-form-item>
          <el-form-item label="表单填写模式">
            <el-select v-model="selectedConfig.formMode" style="width: 100%" @change="syncNodeConfig">
              <el-option label="新建填写" value="create" />
              <el-option label="编辑已有数据" value="edit" />
              <el-option label="补充填写" value="supplement" />
              <el-option label="只读查看" value="readonly" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许暂存">
            <el-switch v-model="selectedConfig.draftAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="提交时校验">
            <el-switch v-model="selectedConfig.validateOnSubmit" active-text="校验" inactive-text="不校验" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="提交按钮文案">
            <el-input v-model="selectedConfig.submitButtonText" placeholder="例如 提交申请" @change="syncNodeConfig" />
          </el-form-item>
        </section>
        <el-alert
          v-else
          class="node-form-section"
          type="info"
          :closable="false"
          show-icon
          title="当前节点不需要绑定表单。"
        />
        <template v-if="selectedConfig.businessType === 'start'">
          <el-form-item label="发起方式">
            <el-select v-model="selectedConfig.startMode" @change="syncNodeConfig">
              <el-option label="手动发起" value="MANUAL" />
              <el-option label="表单提交触发" value="FORM_SUBMIT" />
              <el-option label="定时触发" value="TIMER" />
            </el-select>
          </el-form-item>
          <el-form-item label="发起权限">
            <el-select v-model="selectedConfig.startPermission" @change="syncNodeConfig">
              <el-option label="所有人" value="ALL" />
              <el-option label="指定角色" value="ROLE" />
              <el-option label="指定部门" value="DEPARTMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否需要登录">
            <el-switch v-model="selectedConfig.loginRequired" active-text="需要" inactive-text="不需要" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'condition'">
          <el-divider content-position="left">条件分支配置</el-divider>
          <el-form-item label="分支描述">
            <el-input v-model="selectedConfig.branchDescription" placeholder="例如：请假天数判断" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="条件表达式">
            <el-input v-model="selectedConfig.conditionExpression" type="textarea" :rows="3"
              placeholder="例如：leaveDays > 3" @change="syncNodeConfig" />
            <div class="designer-empty-hint">使用流程变量编写条件，如 leaveDays > 3、amount >= 5000</div>
          </el-form-item>
          <el-form-item label="条件字段">
            <el-input v-model="selectedConfig.conditionField" placeholder="例如：leaveDays" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="默认分支">
            <el-input v-model="selectedConfig.defaultFlow" placeholder="无条件匹配时的兜底分支ID" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'form_fill'">
          <el-form-item label="可编辑字段">
            <el-input
              :model-value="selectedConfig.editableFields.join(',')"
              placeholder="多个字段用英文逗号分隔"
              @change="updateListField('editableFields', $event)"
            />
          </el-form-item>
          <el-form-item label="必填校验">
            <el-input
              :model-value="selectedConfig.requiredFields.join(',')"
              placeholder="多个字段用英文逗号分隔"
              @change="updateListField('requiredFields', $event)"
            />
          </el-form-item>
          <el-form-item label="是否允许上传附件">
            <el-switch v-model="selectedConfig.attachmentAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          </template>

        <template v-else-if="selectedConfig.businessType === 'approval'">
          <el-form-item label="审批人类型">
            <el-select v-model="selectedConfig.assigneeType" @change="syncNodeConfig">
              <el-option label="指定用户" value="USER" />
              <el-option label="指定角色" value="ROLE" />
              <el-option label="发起人主管" value="MANAGER" />
              <el-option label="部门负责人" value="DEPT_LEADER" />
            </el-select>
          </el-form-item>
          <el-form-item label="审批人或角色">
            <el-input v-model="selectedConfig.assigneeValue" placeholder="用户ID、角色编码或部门编码" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="审批方式">
            <el-select v-model="selectedConfig.approvalMode" @change="syncNodeConfig">
              <el-option label="单人审批" value="SINGLE" />
              <el-option label="会签" value="ALL" />
              <el-option label="或签" value="ANY" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否允许转交">
            <el-switch v-model="selectedConfig.transferAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否允许加签">
            <el-switch v-model="selectedConfig.addSignAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="审批意见是否必填">
            <el-switch v-model="selectedConfig.commentRequired" active-text="必填" inactive-text="选填" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="允许驳回">
            <el-switch v-model="selectedConfig.allowReject" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="超时设置">
            <el-input v-model="selectedConfig.timeoutConfig.remindAfter" placeholder="例如 24h 后提醒" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="自动通过规则">
            <el-switch v-model="selectedConfig.approvalRule.enabled" active-text="启用" inactive-text="关闭" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item v-if="selectedConfig.approvalRule.enabled" label="规则字段">
            <el-select v-model="selectedConfig.approvalRule.field" allow-create filterable default-first-option @change="syncNodeConfig">
              <el-option label="请假天数 leaveDays" value="leaveDays" />
              <el-option label="申请天数 days" value="days" />
              <el-option label="金额 amount" value="amount" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedConfig.approvalRule.enabled" label="判断条件">
            <el-input v-model="selectedConfig.approvalRule.value" placeholder="例如 3" @change="syncNodeConfig">
              <template #prepend>
                <el-select v-model="selectedConfig.approvalRule.operator" style="width: 88px" @change="syncNodeConfig">
                  <el-option label="<" value="<" />
                  <el-option label="<=" value="<=" />
                  <el-option label=">" value=">" />
                  <el-option label=">=" value=">=" />
                  <el-option label="==" value="==" />
                  <el-option label="!=" value="!=" />
                </el-select>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="驳回规则">
            <el-select v-model="selectedConfig.rejectRule" @change="syncNodeConfig">
              <el-option label="退回申请人" value="TO_APPLICANT" />
              <el-option label="退回上一节点" value="TO_PREVIOUS" />
              <el-option label="直接结束流程" value="END_PROCESS" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'parallel'">
          <el-form-item label="并行说明">
            <el-input v-model="selectedConfig.parallelDescription" type="textarea" :rows="2" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="等待策略">
            <el-select v-model="selectedConfig.waitStrategy" @change="syncNodeConfig">
              <el-option label="全部完成后继续" value="ALL_COMPLETED" />
              <el-option label="任一完成后继续" value="ANY_COMPLETED" />
            </el-select>
          </el-form-item>
          <el-form-item label="分支完成条件">
            <el-input v-model="selectedConfig.branchCompletionCondition" placeholder="例如 所有审批人已处理" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="异常分支处理">
            <el-select v-model="selectedConfig.exceptionStrategy" @change="syncNodeConfig">
              <el-option label="等待人工处理" value="MANUAL" />
              <el-option label="跳过异常分支" value="SKIP" />
              <el-option label="终止流程" value="TERMINATE" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'notify'">
          <el-form-item label="通知对象">
            <el-select v-model="selectedConfig.notifyTarget" @change="syncNodeConfig">
              <el-option label="申请人" value="APPLICANT" />
              <el-option label="审批人" value="APPROVER" />
              <el-option label="指定用户" value="USER" />
              <el-option label="指定角色" value="ROLE" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知渠道">
            <el-select v-model="selectedConfig.notifyChannel" @change="syncNodeConfig">
              <el-option label="站内信" value="IN_APP" />
              <el-option label="邮件" value="EMAIL" />
              <el-option label="短信" value="SMS" />
              <el-option label="企业微信" value="WE_COM" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知模板">
            <el-input v-model="selectedConfig.notifyTemplate" placeholder="模板编码或模板名称" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="发送时机">
            <el-select v-model="selectedConfig.notifyTiming" @change="syncNodeConfig">
              <el-option label="进入节点时" value="ON_ENTER" />
              <el-option label="流程完成时" value="ON_COMPLETE" />
              <el-option label="流程驳回时" value="ON_REJECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否记录通知日志">
            <el-switch v-model="selectedConfig.notifyLogEnabled" active-text="记录" inactive-text="不记录" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'system_action'">
          <el-form-item label="动作类型">
            <el-select v-model="selectedConfig.actionType" @change="syncNodeConfig">
              <el-option label="调用接口" value="HTTP" />
              <el-option label="写入数据库" value="DB_WRITE" />
              <el-option label="生成编号" value="GENERATE_CODE" />
              <el-option label="创建工单" value="CREATE_TICKET" />
              <el-option label="Webhook" value="WEBHOOK" />
            </el-select>
          </el-form-item>
          <el-form-item label="接口地址或动作标识">
            <el-input v-model="selectedConfig.apiUrl" placeholder="例如 /api/work-order/create" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="请求方式">
            <el-select v-model="selectedConfig.requestMethod" @change="syncNodeConfig">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item label="参数映射">
            <el-input v-model="selectedConfig.parameterMapping" type="textarea" :rows="3" placeholder="{ applicant: form.applicant }" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="失败处理">
            <el-select v-model="selectedConfig.failureStrategy" @change="syncNodeConfig">
              <el-option label="重试" value="RETRY" />
              <el-option label="跳过" value="SKIP" />
              <el-option label="转人工" value="TO_MANUAL" />
              <el-option label="终止流程" value="TERMINATE" />
            </el-select>
          </el-form-item>
          <el-form-item label="最大重试次数">
            <el-input-number v-model="selectedConfig.retryCount" :min="0" :max="10" controls-position="right" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'end'">
          <el-form-item label="结束状态">
            <el-select v-model="selectedConfig.endStatus" @change="syncNodeConfig">
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已取消" value="CANCELED" />
              <el-option label="异常结束" value="ERROR" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否发送完成通知">
            <el-switch v-model="selectedConfig.completionNotifyEnabled" active-text="发送" inactive-text="不发送" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否归档流程数据">
            <el-switch v-model="selectedConfig.archiveEnabled" active-text="归档" inactive-text="不归档" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否锁定表单数据">
            <el-switch v-model="selectedConfig.lockFormData" active-text="锁定" inactive-text="不锁定" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="业务备注">
            <el-input v-model="selectedConfig.remark" type="textarea" :rows="3" placeholder="预留配置" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <div class="node-config-preview">
          <span>预留保存结构</span>
          <pre>{{ JSON.stringify(selectedConfig, null, 2) }}</pre>
        </div>

        <div class="property-footer">
          <el-button round @click="showComingSoon">复制</el-button>
          <el-button round type="danger" plain @click="deleteSelected">删除</el-button>
          <el-button round type="success" @click="saveXml">保存</el-button>
        </div>
      </el-form>
    </aside>

    <el-dialog v-model="xmlVisible" title="当前 BPMN XML" width="760px">
      <el-input v-model="xmlText" type="textarea" :rows="18" />
      <template #footer>
        <el-button round @click="copyXml">复制 XML</el-button>
        <el-button round type="success" @click="xmlVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="从 BPMN XML 回显" width="760px">
      <el-input v-model="importXmlText" type="textarea" :rows="18" placeholder="粘贴已有 BPMN XML" />
      <template #footer>
        <el-button round @click="importVisible = false">取消</el-button>
        <el-button round type="success" @click="importFromText">导入并回显</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateSaveVisible" title="保存为流程模板" width="620px">
      <el-form label-position="top">
        <el-form-item label="模板编码">
          <el-input v-model="templateSaveForm.templateCode" :disabled="Boolean(savedTemplateId)" placeholder="例如 leave_approval_v1" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="templateSaveForm.templateName" placeholder="例如 请假审批流程" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="templateSaveForm.bizTypeId" clearable placeholder="请选择业务类型">
            <el-option v-for="item in bizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定表单">
          <el-select v-model="templateSaveForm.formId" clearable placeholder="请选择已发布表单">
            <el-option v-for="item in forms" :key="item.id" :label="item.formName" :value="item.id" />
          </el-select>
          <div v-if="forms.length === 0" class="designer-empty-hint">暂无已发布表单，可先在表单设计器中创建并发布表单。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="templateSaveVisible = false">取消</el-button>
        <el-button round type="success" :loading="templateSaving" @click="submitTemplateSave">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Check,
  Download,
  EditPen,
  Finished,
  Operation,
  Refresh,
  Share,
  Switch,
  Upload,
  VideoPlay
} from '@element-plus/icons-vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { showComingSoon } from '@/utils/feedback'
import { getBizTypes } from '@/api/bizType'
import { getPublishedForms } from '@/api/formDefinition'
import { createProcessTemplate, getProcessTemplateDetail, updateProcessTemplate } from '@/api/processTemplate'
import { useTemplateStore } from '@/stores/template'
import type { BizType, FormDefinition } from '@/types/workflow'

type BusinessType =
  | 'start'
  | 'form_fill'
  | 'approval'
  | 'condition'
  | 'parallel'
  | 'notify'
  | 'system_action'
  | 'end'
  | 'generic_task'

interface BpmnElement {
  id: string
  type: string
  businessObject?: {
    name?: string
  }
}

interface NodeBusinessConfig {
  nodeId: string
  bpmnType: string
  businessType: BusinessType
  nodeName: string
  formBindingMode: 'none' | 'template_default' | 'node_form'
  formId: number | null
  useTemplateFallback: boolean
  formMode: string
  editableFields: string[]
  readonlyFields: string[]
  hiddenFields: string[]
  requiredFields: string[]
  validateOnSubmit: boolean
  assigneeType: string
  assigneeValue: string
  approvalMode: string
  rejectRule: string
  conditionField: string
  conditionExpression: string
  defaultFlow: string
  notifyTarget: string
  notifyChannel: string
  notifyTemplate: string
  notifyTiming: string
  notifyLogEnabled: boolean
  actionType: string
  apiUrl: string
  requestMethod: string
  parameterMapping: string
  retryCount: number
  timeoutConfig: {
    remindAfter: string
    autoAction: string
  }
  approvalRule: {
    enabled: boolean
    field: string
    operator: string
    value: string | number
    action: string
  }
  endStatus: string
  startMode: string
  startPermission: string
  loginRequired: boolean
  attachmentAllowed: boolean
  draftAllowed: boolean
  submitButtonText: string
  transferAllowed: boolean
  addSignAllowed: boolean
  commentRequired: boolean
  allowReject: boolean
  assignStrategy: string
  assignValue: string
  branchDescription: string
  parallelDescription: string
  waitStrategy: string
  branchCompletionCondition: string
  exceptionStrategy: string
  failureStrategy: string
  completionNotifyEnabled: boolean
  archiveEnabled: boolean
  lockFormData: boolean
  remark: string
}

interface BusinessNodeConfig {
  label: string
  businessType: BusinessType
  bpmnType: string
  defaultName: string
  icon: object
}

const BUSINESS_NODE_CONFIGS: BusinessNodeConfig[] = [
  { label: '流程开始', businessType: 'start', bpmnType: 'bpmn:StartEvent', defaultName: '流程开始', icon: VideoPlay },
  { label: '申请填写', businessType: 'form_fill', bpmnType: 'bpmn:UserTask', defaultName: '填写申请', icon: EditPen },
  { label: '审批处理', businessType: 'approval', bpmnType: 'bpmn:UserTask', defaultName: '审批处理', icon: Check },
  { label: '条件分支', businessType: 'condition', bpmnType: 'bpmn:ExclusiveGateway', defaultName: '条件判断', icon: Switch },
  { label: '并行处理', businessType: 'parallel', bpmnType: 'bpmn:ParallelGateway', defaultName: '并行处理', icon: Share },
  { label: '抄送通知', businessType: 'notify', bpmnType: 'bpmn:SendTask', defaultName: '抄送通知', icon: Bell },
  { label: '系统动作', businessType: 'system_action', bpmnType: 'bpmn:ServiceTask', defaultName: '系统处理', icon: Operation },
  { label: '流程结束', businessType: 'end', bpmnType: 'bpmn:EndEvent', defaultName: '流程结束', icon: Finished }
]
const FLOWABLE_NODE_TYPE_MAP: Record<BusinessType, string> = {
  start: 'bpmn:StartEvent',
  form_fill: 'bpmn:UserTask',
  approval: 'bpmn:UserTask',
  generic_task: 'bpmn:UserTask',
  condition: 'bpmn:ExclusiveGateway',
  parallel: 'bpmn:ParallelGateway',
  notify: 'bpmn:SendTask',
  system_action: 'bpmn:ServiceTask',
  end: 'bpmn:EndEvent'
}

const PALETTE_TOOLTIPS = [
  { selectors: ['[data-action="hand-tool"]', '.bpmn-icon-hand-tool'], text: '拖动画布' },
  { selectors: ['[data-action="lasso-tool"]', '.bpmn-icon-lasso-tool'], text: '框选多个节点' },
  { selectors: ['[data-action="space-tool"]', '.bpmn-icon-space-tool'], text: '调整流程间距' },
  { selectors: ['[data-action="global-connect-tool"]', '.bpmn-icon-connection-multi'], text: '快速连接节点' },
  { selectors: ['[data-action="create.start-event"]', '.bpmn-icon-start-event-none'], text: '开始事件，表示流程开始' },
  { selectors: ['[data-action="create.intermediate-event"]', '.bpmn-icon-intermediate-event-none'], text: '中间事件，表示流程过程中的等待、消息或时间触发' },
  { selectors: ['[data-action="create.end-event"]', '.bpmn-icon-end-event-none'], text: '结束事件，表示流程结束' },
  { selectors: ['[data-action="create.exclusive-gateway"]', '.bpmn-icon-gateway-xor'], text: '排他网关，用于条件判断，只会进入一个分支' },
  { selectors: ['[data-action="create.parallel-gateway"]', '.bpmn-icon-gateway-parallel'], text: '并行网关，用于多个分支同时执行' },
  { selectors: ['[data-action="create.task"]', '.bpmn-icon-task'], text: '普通任务，表示一个待处理工作' },
  { selectors: ['[data-action="create.user-task"]', '.bpmn-icon-user-task'], text: '人工任务，表示需要用户填写、审批或处理' },
  { selectors: ['[data-action="create.service-task"]', '.bpmn-icon-service-task'], text: '服务任务，表示系统自动执行接口、脚本或后台操作' },
  { selectors: ['[data-action="create.data-object"]', '.bpmn-icon-data-object'], text: '数据对象，表示流程中使用的业务数据或表单数据' },
  { selectors: ['[data-action="create.data-store"]', '.bpmn-icon-data-store'], text: '数据存储，表示外部数据库、文件或业务系统' },
  { selectors: ['[data-action="create.group"]', '.bpmn-icon-group'], text: '分组，用于视觉上归类一组节点' },
  { selectors: ['[data-action="create.subprocess-expanded"]', '.bpmn-icon-subprocess-expanded'], text: '子流程，用于封装一段可复用流程' }
]

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  save: [value: string]
  change: [value: string]
}>()

const canvasRef = ref<HTMLDivElement>()
const modeler = ref<any>()
const selectedElement = ref<BpmnElement | null>(null)
const xmlVisible = ref(false)
const importVisible = ref(false)
const xmlText = ref('')
const importXmlText = ref('')
const templateSaveVisible = ref(false)
const templateSaving = ref(false)
const savedTemplateId = ref<number | null>(null)
const bizTypes = ref<BizType[]>([])
const forms = ref<FormDefinition[]>([])
const formsLoading = ref(false)
const FORM_BINDABLE_BUSINESS_TYPES: BusinessType[] = ['start', 'form_fill', 'approval', 'generic_task']
const elementCount = ref(0)
const zoomPercent = ref(100)
const nodeConfigMap = reactive<Record<string, NodeBusinessConfig>>({})
const route = useRoute()
const templateStore = useTemplateStore()
const currentXml = ref(props.modelValue || defaultBpmnXml())
const templateSaveForm = reactive({
  templateCode: '',
  templateName: '未命名流程模板',
  bizTypeId: null as number | null,
  formId: null as number | null
})

const selectedConfig = computed(() => {
  if (!selectedElement.value) return null
  return ensureNodeConfig(selectedElement.value)
})

watch(
  () => props.modelValue,
  async (value) => {
    if (!value || value === currentXml.value || !modeler.value) return
    await importDiagram(value)
  }
)

onMounted(async () => {
  await nextTick()
  modeler.value = new BpmnModeler({
    container: canvasRef.value
  })
  bindModelerEvents()

  // 从查询参数加载模板 BPMN（「查看流程图」入口）
  const templateId = route.query.templateId
  if (templateId && typeof templateId === 'string') {
    try {
      const template = await getProcessTemplateDetail(Number(templateId))
      if (template?.bpmnXml) {
        currentXml.value = template.bpmnXml
        // 同步设置模板 store，使 TopBar 显示当前模板名
        templateStore.setCurrentTemplate({
          id: template.id,
          templateCode: template.templateCode,
          templateName: template.templateName,
          bizTypeId: template.bizTypeId ?? undefined,
          formId: template.formId ?? undefined,
          version: template.version,
          status: template.status ?? '',
          sourceType: template.sourceType,
          bpmnXml: template.bpmnXml,
          nodeConfig: template.nodeConfig,
          formBindConfig: template.formBindConfig
        } as any)
      } else {
        console.warn('[ProcessDesigner] 模板无 BPMN XML，使用默认流程图')
      }
    } catch (err) {
      console.error('[ProcessDesigner] 模板加载失败:', err)
    }
  }

  // 检查是否来自 AI 生成（query 参数 from=ai），如果有则加载 AI 生成的 XML
  const fromAi = route.query.from === 'ai'
  const aiXml = sessionStorage.getItem('ai-generated-bpmn')
  if (fromAi && aiXml) {
    currentXml.value = aiXml
    sessionStorage.removeItem('ai-generated-bpmn') // 用完清理
    templateStore.setCurrentTemplate({
      id: 0,
      templateCode: '',
      templateName: 'AI 生成流程（草稿）',
      status: '',
      sourceType: 'ai_generated',
      bpmnXml: aiXml
    } as any)
  }

  // 确保 XML 非空且合法后再导入
  const xmlToImport = currentXml.value?.trim()
  if (xmlToImport && xmlToImport.startsWith('<?xml')) {
    try {
      await modeler.value.importXML(xmlToImport)
    } catch (importErr) {
      console.error('[ProcessDesigner] importXML 失败，尝试默认流程:', importErr)
      // 回退到默认流程图
      const defaultXml = defaultBpmnXml()
      try {
        currentXml.value = defaultXml
        await modeler.value.importXML(defaultXml)
      } catch (fallbackErr) {
        console.error('[ProcessDesigner] 默认流程导入也失败了:', fallbackErr)
        ElMessage.error('流程图加载失败，请检查 BPMN XML 格式')
        return
      }
    }
  } else {
    // XML 为空或格式异常，加载默认流程
    console.warn('[ProcessDesigner] XML 为空或格式异常，加载默认流程')
    const defaultXml = defaultBpmnXml()
    currentXml.value = defaultXml
    try {
      await modeler.value.importXML(defaultXml)
    } catch (err) {
      console.error('[ProcessDesigner] 默认流程导入失败:', err)
      ElMessage.error('流程图加载失败')
      return
    }
  }

  // 导入成功后的后处理
  const canvas = modeler.value.get('canvas')
  canvas.zoom('fit-viewport', 'auto')
  await syncXml(false)
  refreshStats()
  setTimeout(addPaletteTooltips, 0)

  await Promise.all([loadBizTypeOptions(), loadPublishedFormOptions()])
})

onBeforeUnmount(() => {
  modeler.value?.destroy()
})

function bindModelerEvents() {
  const eventBus = modeler.value.get('eventBus')
  eventBus.on('selection.changed', (event: { newSelection: BpmnElement[] }) => {
    selectedElement.value = event.newSelection[0] || null
    if (selectedElement.value) {
      const config = ensureNodeConfig(selectedElement.value)
      if (isFormBindableNode(config)) {
        void loadPublishedFormOptions()
      }
    }
  })
  eventBus.on('commandStack.changed', async () => {
    await syncXml(false)
    refreshStats()
    addPaletteTooltips()
  })
  eventBus.on('canvas.viewbox.changed', () => {
    const canvas = modeler.value.get('canvas')
    zoomPercent.value = Math.round(canvas.zoom() * 100)
  })
}

async function importDiagram(xml: string) {
  try {
    await modeler.value.importXML(xml)
    const canvas = modeler.value.get('canvas')
    canvas.zoom('fit-viewport', 'auto')
    currentXml.value = xml
    importXmlText.value = xml
    selectedElement.value = null
    await syncXml(false)
    refreshStats()
    setTimeout(addPaletteTooltips, 0)
    ElMessage.success('流程图已回显')
  } catch (error) {
    ElMessage.error('BPMN XML 导入失败')
    console.error(error)
  }
}

async function loadDefaultDiagram() {
  await importDiagram(defaultBpmnXml())
}

async function exportXml() {
  await syncXml(false)
  xmlText.value = currentXml.value
  xmlVisible.value = true
}

async function saveXml() {
  await syncXml(true)
  if (!validateTemplateForFlowablePreparation()) return
  emit('save', currentXml.value)
  await ensureTemplateOptionsLoaded()
  templateSaveVisible.value = true
}

async function ensureTemplateOptionsLoaded() {
  await Promise.all([loadBizTypeOptions(), loadPublishedFormOptions()])
}

async function loadBizTypeOptions() {
  if (bizTypes.value.length > 0) return
  try {
    bizTypes.value = await getBizTypes()
  } catch {
    ElMessage.warning('业务类型加载失败，请检查后端服务。')
  }
}

async function loadPublishedFormOptions(force = false) {
  if (!force && forms.value.length > 0) return
  formsLoading.value = true
  try {
    forms.value = await getPublishedForms()
  } catch {
    ElMessage.warning('已发布表单加载失败，请检查后端服务。')
  } finally {
    formsLoading.value = false
  }
}

async function refreshPublishedForms() {
  await loadPublishedFormOptions(true)
}

function handleFormSelectVisibleChange(visible: boolean) {
  if (visible) {
    void loadPublishedFormOptions()
  }
}

async function submitTemplateSave() {
  if (!templateSaveForm.templateName.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!savedTemplateId.value && !templateSaveForm.templateCode.trim()) {
    ElMessage.warning('请输入模板编码')
    return
  }

  await syncXml(true)
  if (!validateTemplateForFlowablePreparation()) return
  templateSaving.value = true
  try {
    const payload = {
      templateCode: templateSaveForm.templateCode,
      templateName: templateSaveForm.templateName,
      bizTypeId: templateSaveForm.bizTypeId,
      formId: templateSaveForm.formId,
      sourceType: 'manual',
      bpmnXml: currentXml.value,
      nodeConfig: JSON.stringify(buildPersistableNodeConfig()),
      formBindConfig: JSON.stringify({ formId: templateSaveForm.formId }),
      // TODO: 后续接入登录后替换为当前用户 ID。
      createdBy: 1
    }
    if (savedTemplateId.value) {
      await updateProcessTemplate(savedTemplateId.value, payload)
      ElMessage.success('流程模板已更新')
    } else {
      const saved = await createProcessTemplate(payload)
      savedTemplateId.value = saved.id
      ElMessage.success('流程模板已保存')
    }
    templateSaveVisible.value = false
  } finally {
    templateSaving.value = false
  }
}

async function syncXml(emitChange: boolean) {
  if (!modeler.value) return
  const result = await modeler.value.saveXML({ format: true })
  currentXml.value = result.xml || ''
  emit('update:modelValue', currentXml.value)
  if (emitChange) {
    emit('change', currentXml.value)
  }
}

async function importFromText() {
  if (!importXmlText.value.trim()) {
    ElMessage.warning('请先粘贴 BPMN XML')
    return
  }
  await importDiagram(importXmlText.value)
  importVisible.value = false
}

async function copyXml() {
  await navigator.clipboard.writeText(xmlText.value)
  ElMessage.success('BPMN XML 已复制')
}

function createBusinessNode(node: BusinessNodeConfig) {
  if (!modeler.value) return
  const elementFactory = modeler.value.get('elementFactory')
  const modeling = modeler.value.get('modeling')
  const canvas = modeler.value.get('canvas')
  const selection = modeler.value.get('selection')
  const rootElement = canvas.getRootElement()
  const viewbox = canvas.viewbox()
  const position = {
    x: viewbox.x + viewbox.width / 2 - 50 + Math.random() * 80,
    y: viewbox.y + viewbox.height / 2 - 30 + Math.random() * 60
  }
  const shape = elementFactory.createShape({ type: node.bpmnType })
  const created = modeling.createShape(shape, position, rootElement)
  modeling.updateProperties(created, { name: node.defaultName })
  ensureNodeConfig(created, node.defaultName, node.businessType)
  selection.select(created)
  selectedElement.value = created
  ElMessage.success(`已创建：${node.label}`)
}

function ensureNodeConfig(element: BpmnElement, defaultName?: string, businessType?: BusinessType) {
  if (!nodeConfigMap[element.id]) {
    const inferredBusinessType = businessType || inferBusinessType(element)
    const nodeName = defaultName || element.businessObject?.name || getDefaultNodeName(inferredBusinessType, element.type)
    nodeConfigMap[element.id] = createDefaultNodeConfig(element, inferredBusinessType, nodeName)
  } else {
    nodeConfigMap[element.id] = normalizeNodeFormConfig(nodeConfigMap[element.id])
  }
  return nodeConfigMap[element.id]
}

function createDefaultNodeConfig(element: BpmnElement, businessType: BusinessType, nodeName: string): NodeBusinessConfig {
  const base: NodeBusinessConfig = {
    nodeId: element.id,
    bpmnType: element.type,
    businessType,
    nodeName,
    formBindingMode: 'none',
    formId: null,
    useTemplateFallback: false,
    editableFields: [],
    readonlyFields: [],
    hiddenFields: [],
    requiredFields: [],
    validateOnSubmit: true,
    assigneeType: 'ROLE',
    assigneeValue: '',
    assignStrategy: '',
    assignValue: '',
    approvalMode: 'SINGLE',
    rejectRule: 'TO_APPLICANT',
    conditionField: '',
    conditionExpression: '',
    defaultFlow: '',
    notifyTarget: 'APPLICANT',
    notifyChannel: 'IN_APP',
    notifyTemplate: '',
    notifyTiming: 'ON_ENTER',
    notifyLogEnabled: true,
    actionType: 'HTTP',
    apiUrl: '',
    requestMethod: 'POST',
    parameterMapping: '',
    retryCount: 0,
    timeoutConfig: {
      remindAfter: '',
      autoAction: ''
    },
    approvalRule: {
      enabled: false,
      field: 'leaveDays',
      operator: '<',
      value: 3,
      action: 'approve'
    },
    endStatus: 'COMPLETED',
    startMode: 'MANUAL',
    startPermission: 'ALL',
    loginRequired: true,
    formMode: 'edit',
    attachmentAllowed: true,
    draftAllowed: true,
    submitButtonText: '提交申请',
    transferAllowed: false,
    addSignAllowed: false,
    commentRequired: true,
    allowReject: false,
    branchDescription: '',
    parallelDescription: '',
    waitStrategy: 'ALL_COMPLETED',
    branchCompletionCondition: '',
    exceptionStrategy: 'MANUAL',
    failureStrategy: 'RETRY',
    completionNotifyEnabled: true,
    archiveEnabled: true,
    lockFormData: true,
    remark: ''
  }

  applyDefaultFormBinding(base, businessType)

  if (businessType === 'approval') {
    base.assigneeType = 'MANAGER'
  }
  if (businessType === 'notify') {
    base.actionType = 'NOTIFY'
    base.notifyTiming = 'ON_COMPLETE'
  }
  if (businessType === 'system_action') {
    base.actionType = 'HTTP'
    base.failureStrategy = 'RETRY'
    base.retryCount = 3
  }
  return base
}

function inferBusinessType(element: BpmnElement): BusinessType {
  const typeMap: Record<string, BusinessType> = {
    'bpmn:StartEvent': 'start',
    'bpmn:UserTask': 'approval',
    'bpmn:Task': 'generic_task',
    'bpmn:ServiceTask': 'system_action',
    'bpmn:SendTask': 'notify',
    'bpmn:ExclusiveGateway': 'condition',
    'bpmn:ParallelGateway': 'parallel',
    'bpmn:EndEvent': 'end'
  }
  return typeMap[element.type] || 'generic_task'
}

function getDefaultNodeName(businessType: BusinessType, bpmnType: string) {
  const config = BUSINESS_NODE_CONFIGS.find((item) => item.businessType === businessType)
  if (config) return config.defaultName
  const names: Record<string, string> = {
    'bpmn:Task': '普通任务',
    'bpmn:IntermediateThrowEvent': '中间事件',
    'bpmn:DataObjectReference': '数据对象',
    'bpmn:DataStoreReference': '数据存储',
    'bpmn:Group': '分组',
    'bpmn:SubProcess': '子流程'
  }
  return names[bpmnType] || '流程节点'
}

function getBusinessLabel(businessType: BusinessType) {
  const label = BUSINESS_NODE_CONFIGS.find((item) => item.businessType === businessType)?.label
  return label || '普通任务'
}

function isFormBindableNode(config?: NodeBusinessConfig | null) {
  return Boolean(config && FORM_BINDABLE_BUSINESS_TYPES.includes(config.businessType))
}

async function handleFormBindingModeChange() {
  if (!selectedConfig.value) return
  if (selectedConfig.value.formBindingMode === 'none') {
    selectedConfig.value.formId = null
    selectedConfig.value.useTemplateFallback = false
  }
  if (selectedConfig.value.formBindingMode === 'template_default') {
    selectedConfig.value.formId = null
    selectedConfig.value.useTemplateFallback = true
  }
  if (selectedConfig.value.formBindingMode === 'node_form') {
    selectedConfig.value.useTemplateFallback = true
    await loadPublishedFormOptions()
  }
  syncNodeConfig()
}

function applyDefaultFormBinding(config: NodeBusinessConfig, businessType: BusinessType) {
  if (businessType === 'start') {
    config.formBindingMode = 'template_default'
    config.formId = null
    config.useTemplateFallback = true
    config.formMode = 'create'
    config.validateOnSubmit = true
    config.draftAllowed = true
    config.submitButtonText = '提交申请'
    return
  }
  if (businessType === 'form_fill') {
    config.formBindingMode = 'node_form'
    config.formId = null
    config.useTemplateFallback = true
    config.formMode = 'supplement'
    config.validateOnSubmit = true
    config.draftAllowed = true
    config.submitButtonText = '提交表单'
    return
  }
  if (businessType === 'approval') {
    config.formBindingMode = 'none'
    config.formId = null
    config.useTemplateFallback = false
    config.formMode = 'edit'
    config.validateOnSubmit = true
    config.draftAllowed = false
    config.submitButtonText = '提交审批'
    return
  }
  if (businessType === 'generic_task') {
    config.formBindingMode = 'none'
    config.formId = null
    config.useTemplateFallback = false
    config.formMode = 'edit'
    config.validateOnSubmit = true
    config.draftAllowed = true
    config.submitButtonText = '提交任务'
  }
}

function normalizeNodeFormConfig(config: NodeBusinessConfig): NodeBusinessConfig {
  const normalized = config
  normalized.approvalRule = {
    enabled: normalized.approvalRule?.enabled ?? false,
    field: normalized.approvalRule?.field || 'leaveDays',
    operator: normalized.approvalRule?.operator || '<',
    value: normalized.approvalRule?.value ?? 3,
    action: normalized.approvalRule?.action || 'approve'
  }
  if (!isFormBindableNode(normalized)) {
    delete (normalized as Partial<NodeBusinessConfig>).formId
    delete (normalized as Partial<NodeBusinessConfig>).formBindingMode
    delete (normalized as Partial<NodeBusinessConfig>).useTemplateFallback
    return normalized
  }
  normalized.formBindingMode = normalized.formBindingMode || (normalized.formId ? 'node_form' : 'none')
  normalized.formId = normalized.formId ? Number(normalized.formId) : null
  normalized.useTemplateFallback = normalized.formBindingMode === 'template_default' || normalized.formBindingMode === 'node_form'
  normalized.formMode = normalized.formMode || 'edit'
  normalized.editableFields = normalized.editableFields || []
  normalized.readonlyFields = normalized.readonlyFields || []
  normalized.hiddenFields = normalized.hiddenFields || []
  normalized.requiredFields = normalized.requiredFields || []
  normalized.validateOnSubmit = normalized.validateOnSubmit ?? true
  normalized.draftAllowed = normalized.draftAllowed ?? true
  normalized.submitButtonText = normalized.submitButtonText || '提交'
  return normalized
}

function buildPersistableNodeConfig() {
  return Object.fromEntries(
    Object.entries(nodeConfigMap).map(([nodeId, config]) => [nodeId, normalizeNodeFormConfig({ ...config })])
  )
}

function validateTemplateForFlowablePreparation() {
  if (!validateBpmnXmlReady()) return false
  if (!validateNodeConfigAlignment()) return false
  if (!validateNodeFormBindings()) return false
  return true
}

function validateBpmnXmlReady() {
  const xml = currentXml.value.trim()
  if (!xml) {
    ElMessage.warning('BPMN XML 不能为空，请先绘制或导入流程图。')
    return false
  }
  if (!xml.includes('<bpmn:definitions') || !xml.includes('<bpmn:process')) {
    ElMessage.warning('BPMN XML 不完整，必须包含 definitions 和 process。')
    return false
  }
  return true
}

function validateNodeConfigAlignment() {
  if (!modeler.value) return true
  const elementRegistry = modeler.value.get('elementRegistry')
  for (const [nodeId, config] of Object.entries(nodeConfigMap)) {
    if (nodeId !== config.nodeId) {
      ElMessage.warning('节点配置不一致：配置 key ' + nodeId + ' 与 nodeId ' + config.nodeId + ' 不一致。')
      return false
    }
    const element = elementRegistry.get(nodeId)
    if (!element) {
      ElMessage.warning('节点配置不一致：BPMN XML 中找不到节点 ' + nodeId + '。')
      return false
    }
    if (config.bpmnType && element.type !== config.bpmnType) {
      ElMessage.warning('节点配置不一致：节点 ' + nodeId + ' 的 BPMN 类型已变化，请重新确认节点属性。')
      return false
    }
  }
  return true
}

function validateNodeFormBindings() {
  for (const config of Object.values(nodeConfigMap)) {
    const normalized = normalizeNodeFormConfig(config)
    if (isFormBindableNode(normalized) && normalized.formBindingMode === 'node_form' && !normalized.formId) {
      ElMessage.warning('节点【' + normalized.nodeName + '】已选择绑定节点表单，请选择具体表单。')
      return false
    }
  }
  return true
}
function updateSelectedName() {
  if (!selectedElement.value || !modeler.value || !selectedConfig.value) return
  const modeling = modeler.value.get('modeling')
  modeling.updateProperties(selectedElement.value, { name: selectedConfig.value.nodeName })
  syncNodeConfig()
}

function updateListField(field: 'editableFields' | 'requiredFields', value: string | number | boolean) {
  if (!selectedConfig.value) return
  selectedConfig.value[field] = String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  syncNodeConfig()
}

function syncNodeConfig() {
  // 将前端 assigneeType 映射为后端 assignStrategy
  if (selectedConfig.value) {
    const typeMap: Record<string, string> = {
      'MANAGER': 'DIRECT_SUPERVISOR',
      'DEPT_LEADER': 'DEPARTMENT_MANAGER',
      'USER': 'SPECIFIC_USERS',
      'ROLE': 'ROLE'
    }
    if (selectedConfig.value.assigneeType) {
      selectedConfig.value.assignStrategy = typeMap[selectedConfig.value.assigneeType] || ''
    }
    if (selectedConfig.value.assigneeValue) {
      selectedConfig.value.assignValue = selectedConfig.value.assigneeValue
    }
  }
  void syncXml(false)
}

function deleteSelected() {
  if (!selectedElement.value || !modeler.value) return
  const modeling = modeler.value.get('modeling')
  const nodeId = selectedElement.value.id
  modeling.removeElements([selectedElement.value])
  delete nodeConfigMap[nodeId]
  selectedElement.value = null
}

function refreshStats() {
  if (!modeler.value) return
  const elementRegistry = modeler.value.get('elementRegistry')
  elementCount.value = elementRegistry.filter((element: BpmnElement) => !element.type.includes('Label')).length
}

function addPaletteTooltips() {
  if (!canvasRef.value) return
  const entries = canvasRef.value.querySelectorAll('.djs-palette .entry')
  entries.forEach((entry) => {
    const tooltip = findPaletteTooltip(entry as HTMLElement)
    entry.setAttribute('title', tooltip)
    entry.setAttribute('aria-label', tooltip)
  })
}

function findPaletteTooltip(entry: HTMLElement) {
  for (const item of PALETTE_TOOLTIPS) {
    if (item.selectors.some((selector) => entry.matches(selector) || Boolean(entry.querySelector(selector)))) {
      return item.text
    }
  }
  return 'BPMN 建模工具'
}

function defaultBpmnXml() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_AI_Flow" targetNamespace="http://ai-flow/process">
  <bpmn:process id="Process_Leave_Approval" name="员工请假审批流程" isExecutable="true">
    <bpmn:startEvent id="StartEvent_Apply" name="提交申请">
      <bpmn:outgoing>Flow_Start_To_Task</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="UserTask_ManagerApprove" name="主管审批">
      <bpmn:incoming>Flow_Start_To_Task</bpmn:incoming>
      <bpmn:outgoing>Flow_Task_To_Gateway</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_Approved" name="是否通过">
      <bpmn:incoming>Flow_Task_To_Gateway</bpmn:incoming>
      <bpmn:outgoing>Flow_Gateway_To_End</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:endEvent id="EndEvent_Done" name="流程结束">
      <bpmn:incoming>Flow_Gateway_To_End</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_Start_To_Task" sourceRef="StartEvent_Apply" targetRef="UserTask_ManagerApprove" />
    <bpmn:sequenceFlow id="Flow_Task_To_Gateway" sourceRef="UserTask_ManagerApprove" targetRef="Gateway_Approved" />
    <bpmn:sequenceFlow id="Flow_Gateway_To_End" sourceRef="Gateway_Approved" targetRef="EndEvent_Done" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_AI_Flow">
    <bpmndi:BPMNPlane id="BPMNPlane_AI_Flow" bpmnElement="Process_Leave_Approval">
      <bpmndi:BPMNShape id="StartEvent_Apply_di" bpmnElement="StartEvent_Apply">
        <dc:Bounds x="180" y="180" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="174" y="223" width="48" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UserTask_ManagerApprove_di" bpmnElement="UserTask_ManagerApprove">
        <dc:Bounds x="280" y="158" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Approved_di" bpmnElement="Gateway_Approved" isMarkerVisible="true">
        <dc:Bounds x="470" y="173" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="470" y="230" width="50" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Done_di" bpmnElement="EndEvent_Done">
        <dc:Bounds x="590" y="180" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="584" y="223" width="48" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_To_Task_di" bpmnElement="Flow_Start_To_Task">
        <di:waypoint x="216" y="198" />
        <di:waypoint x="280" y="198" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Task_To_Gateway_di" bpmnElement="Flow_Task_To_Gateway">
        <di:waypoint x="400" y="198" />
        <di:waypoint x="470" y="198" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Gateway_To_End_di" bpmnElement="Flow_Gateway_To_End">
        <di:waypoint x="520" y="198" />
        <di:waypoint x="590" y="198" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}
</script>
