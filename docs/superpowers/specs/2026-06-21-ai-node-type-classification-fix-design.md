# AI Process Flow Node Type Classification Fix

**Date:** 2026-06-21  
**Status:** Approved  
**Scope:** `backend/src/main/java/com/aiflow/service/AiProcessService.java`

## Problem

AI-generated process flows frequently misclassify node `businessType`:
- "主管审批" (supervisor approval) identified as `form_fill` instead of `approval`
- "填写申请表" (fill form) identified as `approval` instead of `form_fill`
- Other cross-type confusions between `form_fill`, `approval`, `notify`, `system_action`

Root cause: The LLM (DeepSeek) is solely responsible for classification with only a short text prompt guiding it. No post-processing validation exists.

## Solution: Prompt Optimization + Post-Processing Rule Engine

### Part 1: Enhanced SYSTEM_PROMPT

1. **Keyword-to-type mapping rules** — explicit priority rules linking Chinese keywords to business types
2. **Few-shot examples** — complete JSON output examples for 3 common process types (leave, expense, purchase)
3. **Negative examples** — show common mistakes explicitly marked as wrong
4. **Lower temperature** — 0.3 → 0.1 for more deterministic output

### Part 2: Post-Processing Rule Engine

A `validateAndCorrectNodeConfig()` method runs after AI response parsing:

| Rule | Trigger | Action |
|------|---------|--------|
| Keyword → approval | nodeName matches 审批/审核/批准/核准/复核/签批/阅批 but businessType ≠ approval | Auto-correct to `approval` |
| Keyword → form_fill | nodeName matches 填写/提交/录入/上传/申请/补录 but businessType ≠ form_fill | Auto-correct to `form_fill` |
| Keyword → notify | nodeName matches 通知/抄送/知会 but businessType ≠ notify | Auto-correct to `notify` |
| Element type sanity | bpmn:serviceTask + businessType=approval | Change BPMN element to userTask |
| Element type sanity | bpmn:userTask + businessType=notify | Change BPMN element to serviceTask |
| Missing approval config | businessType=approval, no approvalMode | Default to `SINGLE` |
| Missing approval config | businessType=approval, no assignStrategy | Default to `DIRECT_SUPERVISOR` |
| Structural | No `start` node | Prepend one |
| Structural | No `end` node | Append one |

All corrections log at WARN level for auditability.

### Part 3: Changes

| File | Change |
|------|--------|
| `backend/.../service/AiProcessService.java` | Enhanced SYSTEM_PROMPT + new `validateAndCorrectNodeConfig()` method |

Single file change. No frontend, database, or API contract changes.
