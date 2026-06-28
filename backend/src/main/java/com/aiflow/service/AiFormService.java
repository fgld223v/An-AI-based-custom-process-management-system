package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiGenerateFormResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 表单生成服务。
 *
 * <p>核心职责：接收用户自然语言描述（如"员工请假申请表单"），调用 DeepSeek 大模型
 * 生成企业级数据采集表单的字段配置（fieldList）和布局配置（formSchema）。</p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>构建包含表单设计规则的 System Prompt 和用户描述的请求体</li>
 *   <li>调用 DeepSeek Chat Completions API</li>
 *   <li>提取并清理 JSON 响应</li>
 *   <li>兼容处理 fieldList 的两种格式（JSON 数组 / JSON 字符串）</li>
 *   <li>后处理过滤审批类字段（表单只做数据采集，不做审批决策）</li>
 *   <li>自动注入跨字段校验规则（如 endDate >= startDate）</li>
 * </ol>
 *
 * <p>关键约束：生成的表单仅用于数据采集，绝对禁止包含审批/审核类字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFormService {

    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    /** DeepSeek System Prompt：表单设计规则，强调只生成业务数据字段，禁止审批字段 */
    private static final String SYSTEM_PROMPT = """
        你是一个企业级数据采集表单设计专家。根据用户描述生成完整、实用的表单字段配置。

        输出必须是合法的 JSON 对象，不要包裹在 Markdown 代码块中：

        {
          "fieldList": [
            {"field":"applicant","label":"申请人","type":"text","required":true},
            {"field":"leaveType","label":"请假类型","type":"select","required":true,"options":[{"label":"事假","value":"事假"},{"label":"病假","value":"病假"}]},
            {"field":"startDate","label":"开始日期","type":"date","required":true},
            {"field":"endDate","label":"结束日期","type":"date","required":true},
            {"field":"days","label":"请假天数","type":"number","required":true},
            {"field":"reason","label":"请假原因","type":"textarea","required":true}
          ],
          "formSchema": {
            "layout":"vertical",
            "sections":[
              {"title":"基本信息","fields":["applicant","leaveType","startDate","endDate","days"]},
              {"title":"补充信息","fields":["reason"]}
            ]
          }
        }

        规则：
        1. fieldList 必须是 JSON 数组（不是字符串！），生成 5-10 个字段
        2. 【最高优先级限制 — 绝对禁止】你生成的是数据采集表单，仅用于用户填写和提交业务数据。
           绝对禁止生成以下任何字段，无论用户描述中是否出现相关词汇：
           × 审批意见、审批结果、审批结论、审批状态、审批结构、审批决定
           × 审核意见、审核结果、审核结论、审核状态
           × 批准意见、核准意见、签批意见、阅批意见、批复意见
           × 处理意见、领导意见、上级意见、主管意见、经理意见、审批人
           × 是否同意、是否批准、是否通过、同意标识、审批标识
           × 任何 label 或 field 中包含「审批」「审核」「批准」「核准」「签批」
             「阅批」「批复」「处理意见」的字段
           记住：表单 = 数据采集，不是审批决策。用户只是填写信息，不是做审批决定。
           你永远只生成业务数据字段（姓名、部门、日期、金额、事由、说明、附件等）。
        3. 每个字段必须包含：field（英文驼峰）、label（中文）、type、required
        4. type 取值：text、textarea、number、select、date、datetime、radio、checkbox、upload
        5. select/radio/checkbox 必须包含 options 数组，每个 option 含 label 和 value
        6. formSchema 是 JSON 对象（不是字符串！），含 layout（"vertical"）和 sections 数组
        7. 别用 fieldName/fieldLabel/fieldType，用 field/label/type
        8. 【再次强调】不要审批字段！不要审批字段！不要审批字段！
        """;

    /**
     * 根据用户自然语言描述，调用 DeepSeek 生成表单字段配置。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>构建请求体 — System Prompt 强调只生成业务数据字段，禁止审批字段</li>
     *   <li>调用 DeepSeek API — temperature=0.3 平衡创造性与稳定性</li>
     *   <li>提取 JSON — 从 OpenAI 格式响应中解析 choices[0].message.content</li>
     *   <li>清理 Markdown 代码块包裹</li>
     *   <li>兼容处理 fieldList — 支持 AI 返回 JSON 数组或 JSON 字符串两种格式</li>
     *   <li>后处理过滤 — 移除 AI 可能误生成的审批/审核类字段</li>
     *   <li>兼容处理 formSchema — 支持对象或字符串两种格式，缺失时兜底生成</li>
     *   <li>校验 fieldList 为合法 JSON</li>
     *   <li>注入跨字段校验规则 — 自动为 endDate 添加 >=startDate 规则</li>
     * </ol>
     *
     * @param description 用户描述的表单需求
     * @return 包含 fieldList 和 formSchema JSON 字符串的响应对象
     * @throws BusinessException AI 调用失败、响应解析失败或生成结果无效时抛出
     */
    public AiGenerateFormResponse generateForm(String description) {
        log.info("开始生成表单，用户输入长度：{}", description.length());

        // 1. 构建请求体 — System Prompt 强调禁止审批字段，temperature=0.3 保证合理创造性
        Map<String, Object> requestBody = Map.of(
            "model", aiConfig.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", description)
            ),
            "temperature", 0.3,
            "max_tokens", 4096
        );

        // 2. 调用 DeepSeek API
        String responseBody;
        try {
            responseBody = deepseekWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(body -> new RuntimeException("DeepSeek API 调用失败：" + body)))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .block();
        } catch (Exception e) {
            log.error("DeepSeek API 调用异常", e);
            throw new BusinessException("AI 服务调用失败：" + e.getMessage());
        }

        if (responseBody == null) {
            throw new BusinessException("AI 服务返回为空");
        }

        // 3. 提取 DeepSeek 回复中的 JSON
        String json;
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 服务返回数据格式异常：无 choices");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            json = (String) message.get("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败", e);
            throw new BusinessException("AI 服务响应解析失败");
        }

        // 4. 清理 Markdown 包裹
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }

        // 5. 解析为 Map（通用格式），兼容 AI 返回 string 或 array/object 两种格式
        Map<String, Object> resultMap;
        try {
            resultMap = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析 AI 生成的表单 JSON 失败：{}", json);
            throw new BusinessException("AI 生成的表单格式有误，请重试");
        }

        // 6. 提取 fieldList — 兼容 AI 可能返回的两种格式：
        //    格式A（JSON 数组）: "fieldList": [{"field":"name","label":"姓名",...}, ...]
        //    格式B（JSON 字符串）: "fieldList": "[{\"field\":\"name\",\"label\":\"姓名\",...}, ...]"
        //    统一转换为 JSON 字符串便于存储和后续处理
        Object fieldListObj = resultMap.get("fieldList");
        String fieldListStr;
        if (fieldListObj instanceof String s) {
            fieldListStr = s;
        } else if (fieldListObj instanceof List<?> list) {
            try {
                fieldListStr = objectMapper.writeValueAsString(list);
            } catch (Exception e) {
                throw new BusinessException("fieldList 序列化失败：" + e.getMessage());
            }
        } else {
            throw new BusinessException("AI 未生成有效的字段列表（fieldList 格式异常），请重试");
        }

        // 6b. 后处理：过滤掉 AI 可能误生成的审批类字段（如审批结果、审批意见等）
        fieldListStr = stripApprovalFields(fieldListStr);

        // 7. 提取 formSchema — 同样兼容对象和字符串两种格式
        Object formSchemaObj = resultMap.get("formSchema");
        String formSchemaStr;
        if (formSchemaObj instanceof String s) {
            formSchemaStr = s;
        } else if (formSchemaObj instanceof Map<?, ?> map) {
            try {
                formSchemaStr = objectMapper.writeValueAsString(map);
            } catch (Exception e) {
                formSchemaStr = "{\"layout\":\"vertical\",\"sections\":[]}";
            }
        } else {
            // formSchema 为可选，兜底生成一个
            formSchemaStr = "{\"layout\":\"vertical\",\"sections\":[]}";
        }

        // 8. 校验 fieldList 非空且为有效 JSON，并注入跨字段校验规则
        if (fieldListStr.isBlank()) {
            throw new BusinessException("AI 未生成有效的字段列表，请重试");
        }
        try {
            objectMapper.readTree(fieldListStr); // 验证是合法 JSON
        } catch (Exception e) {
            log.error("fieldList 不是合法 JSON：{}", fieldListStr);
            throw new BusinessException("AI 生成的字段列表格式异常，请重试");
        }

        // 9. 后处理：注入跨字段校验规则
        //    自动识别 endDate/startDate 配对，为 endDate 添加 >=startDate 的校验规则
        //    注意：不再无条件注入审批字段（approvalResult/approvalComment），
        //    审批字段应由 AI 根据用户描述自行判断是否需要，避免非审批表单出现"审批结果"字段
        fieldListStr = injectDateRangeRules(fieldListStr, objectMapper);

        AiGenerateFormResponse result = new AiGenerateFormResponse(fieldListStr, formSchemaStr);
        log.info("表单生成成功，fieldList 长度：{}，字段数：{}",
                fieldListStr.length(),
                fieldListObj instanceof List<?> l ? l.size() : "?");
        return result;
    }

    /**
     * 自动为日期范围字段注入跨字段校验规则。
     *
     * <p>识别逻辑：扫描 fieldList 中 field 名含 "start" + "date"/"time" 和
     * "end" + "date"/"time" 的字段配对，为 end 字段自动添加
     * gte（大于等于）校验规则，确保结束日期不早于开始日期。</p>
     */
    private String injectDateRangeRules(String fieldListJson, ObjectMapper mapper) {
        try {
            List<Map<String, Object>> fields = mapper.readValue(fieldListJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            // 查找 startDate / endDate 配对
            Map<String, Object> startField = null;
            Map<String, Object> endField = null;
            for (Map<String, Object> f : fields) {
                String fieldName = String.valueOf(f.get("field")).toLowerCase();
                if (fieldName.contains("start") && (fieldName.contains("date") || fieldName.contains("time"))) {
                    startField = f;
                }
                if (fieldName.contains("end") && (fieldName.contains("date") || fieldName.contains("time"))) {
                    endField = f;
                }
            }
            if (startField != null && endField != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rules = (List<Map<String, Object>>) endField.get("rules");
                if (rules == null) {
                    rules = new java.util.ArrayList<>();
                    endField.put("rules", rules);
                }
                final String sfField = String.valueOf(startField.get("field"));
                boolean hasGteRule = rules.stream().anyMatch(r ->
                        "gte".equals(r.get("op")) && sfField.equals(r.get("targetField")));
                if (!hasGteRule) {
                    Map<String, Object> rule = new java.util.LinkedHashMap<>();
                    rule.put("op", "gte");
                    rule.put("targetField", String.valueOf(startField.get("field")));
                    rule.put("targetLabel", String.valueOf(startField.get("label")));
                    rule.put("message", String.valueOf(endField.get("label")) + "必须≥" + String.valueOf(startField.get("label")));
                    rules.add(rule);
                    log.info("已为 {} 注入跨字段校验规则：{} >= {}",
                            endField.get("field"), endField.get("field"), startField.get("field"));
                }
                return mapper.writeValueAsString(fields);
            }
            return fieldListJson;
        } catch (Exception e) {
            log.warn("日期范围规则注入失败: {}", e.getMessage());
            return fieldListJson;
        }
    }

    /** 审批类关键词 — 字段 label 或 field 名包含任一关键词即判定为审批字段并移除 */
    private static final Set<String> APPROVAL_KEYWORDS = Set.of(
            "审批", "审核", "批准", "核准", "签批", "阅批", "批复",
            "处理意见", "领导意见", "上级意见", "主管意见", "经理意见",
            "是否同意", "是否批准", "是否通过", "审批人"
    );

    /** 审批类 field 名（英文）正则 — 匹配 approval/audit/review + Result/Comment 等组合 */
    private static final Pattern APPROVAL_FIELD_PATTERN = Pattern.compile(
            "^(approval|audit|review)(Result|Comment|Opinion|Status|Decision|Conclusion|Node|Config|Structure|Suggestion)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 从 fieldList JSON 中移除所有审批/审核类字段。
     *
     * <p>双重检测机制：中文关键词匹配（label/field 名包含审批相关词）+
     * 英文模式匹配（approvalResult、auditComment 等正则）。</p>
     *
     * @return 清理后的 JSON 字符串；如果所有字段都被过滤则抛出 BusinessException
     */
    private String stripApprovalFields(String fieldListJson) {
        try {
            List<Map<String, Object>> fields = objectMapper.readValue(fieldListJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> cleaned = new ArrayList<>();
            int removed = 0;
            for (Map<String, Object> field : fields) {
                String label = String.valueOf(field.getOrDefault("label", ""));
                String fieldName = String.valueOf(field.getOrDefault("field", ""));
                if (isApprovalField(label, fieldName)) {
                    log.warn("【审批字段过滤】已移除字段「{}」(field={})，表单不应包含审批类字段",
                            label, fieldName);
                    removed++;
                    continue;
                }
                cleaned.add(field);
            }
            if (removed > 0) {
                log.info("审批字段过滤完成：共移除 {} 个审批类字段，保留 {} 个业务字段",
                        removed, cleaned.size());
                if (cleaned.isEmpty()) {
                    throw new BusinessException("AI 生成的所有字段均为审批类字段，已被过滤。请用更具体的业务描述重试（如「员工请假申请表单：包含姓名、部门、请假类型、开始日期、结束日期、请假原因」）");
                }
                return objectMapper.writeValueAsString(cleaned);
            }
            return fieldListJson;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("审批字段过滤失败，保留原始 fieldList: {}", e.getMessage());
            return fieldListJson;
        }
    }

    /** 判断一个字段是否为审批/审核类字段 */
    private boolean isApprovalField(String label, String fieldName) {
        // 中文关键词检查
        for (String kw : APPROVAL_KEYWORDS) {
            if (label.contains(kw)) return true;
        }
        // 英文 field 名模式匹配
        if (APPROVAL_FIELD_PATTERN.matcher(fieldName).matches()) return true;
        // field 名中包含中文审批关键词（兜底）
        for (String kw : APPROVAL_KEYWORDS) {
            if (fieldName.contains(kw)) return true;
        }
        return false;
    }
}
