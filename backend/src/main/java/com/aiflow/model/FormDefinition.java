package com.aiflow.model;

import com.aiflow.enums.FormStatus;
import com.aiflow.enums.FormStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表单定义表 (form_definition)
 * 存储表单模板的元数据，包括字段列表、表单 Schema 及版本信息，供流程节点绑定使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "form_definition")
public class FormDefinition {

    /** 表单定义主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 表单编码，用于唯一标识和版本管理 */
    @Column(name = "form_code")
    private String formCode;

    /** 表单名称 */
    @Column(name = "form_name")
    private String formName;

    /** 所属业务类型 ID */
    @Column(name = "biz_type_id")
    private Long bizTypeId;

    /** 表单版本号 */
    @Column(name = "version")
    private Integer version;

    /** 表单状态：draft(草稿) / published(已发布) / disabled(已停用) */
    @Convert(converter = FormStatusConverter.class)
    @Column(name = "status", columnDefinition = "ENUM('draft','published','disabled')")
    private FormStatus status;

    /** 字段列表 JSON，定义表单包含的所有字段 */
    @Column(name = "field_list", columnDefinition = "JSON")
    private String fieldList;

    /** 表单 Schema JSON，定义表单布局和渲染规则 */
    @Column(name = "form_schema", columnDefinition = "JSON")
    private String formSchema;

    /** 创建者用户 ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 来源类型（如 ai_generated / manual / market_copy） */
    @Column(name = "source_type")
    private String sourceType;

    /** 源表单 ID，当从其他表单复制时记录来源 */
    @Column(name = "source_form_id")
    private Long sourceFormId;

    /** 发布时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
