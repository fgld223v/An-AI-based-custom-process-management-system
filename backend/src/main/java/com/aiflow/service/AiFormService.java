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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiFormService {

    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是一个企业级表单设计专家。根据用户描述生成完整、实用的表单字段配置。

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
        2. 审批节点必须额外包含：审批意见(label:"审批意见",type:"textarea",required:true)、审批结果(label:"审批结果",type:"select",options:[{"label":"同意","value":"agree"},{"label":"驳回","value":"reject"},{"label":"需修改","value":"modify"}],required:true)
        3. 每个字段必须包含：field（英文驼峰）、label（中文）、type、required
        4. type 取值：text、textarea、number、select、date、datetime、radio、checkbox、upload
        5. select/radio/checkbox 必须包含 options 数组，每个 option 含 label 和 value
        6. formSchema 是 JSON 对象（不是字符串！），含 layout（"vertical"）和 sections 数组
        7. 别用 fieldName/fieldLabel/fieldType，用 field/label/type
        """;

    public AiGenerateFormResponse generateForm(String description) {
        log.info("开始生成表单，用户输入长度：{}", description.length());

        // 1. 构建请求体
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

        // 6. 提取 fieldList — 兼容两种格式：
        //    新格式: "fieldList": [{...}, ...]  (JSON 数组)
        //    旧格式: "fieldList": "[{...},...]"  (JSON 字符串)
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

        // 7. 提取 formSchema — 同样兼容两种格式
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

        // 8. 校验 fieldList 非空且为有效 JSON
        if (fieldListStr.isBlank()) {
            throw new BusinessException("AI 未生成有效的字段列表，请重试");
        }
        try {
            objectMapper.readTree(fieldListStr); // 验证是合法 JSON
        } catch (Exception e) {
            log.error("fieldList 不是合法 JSON：{}", fieldListStr);
            throw new BusinessException("AI 生成的字段列表格式异常，请重试");
        }

        AiGenerateFormResponse result = new AiGenerateFormResponse(fieldListStr, formSchemaStr);
        log.info("表单生成成功，fieldList 长度：{}，字段数：{}",
                fieldListStr.length(),
                fieldListObj instanceof List<?> l ? l.size() : "?");
        return result;
    }
}
