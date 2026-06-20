<template>
  <div class="market-page">
    <section class="market-hero">
      <div>
        <el-tag type="success" effect="plain">模板市场</el-tag>
        <h1>可复用流程模板</h1>
        <p>从已上架的流程模板中快速复制，沉淀请假、报销、采购、合同审批等常用流程。</p>
      </div>
      <el-button round :icon="Refresh" @click="loadData">刷新</el-button>
    </section>

    <section class="market-filter">
      <el-input v-model="keyword" clearable placeholder="搜索标题或说明" />
      <el-select v-model="selectedBizTypeId" clearable placeholder="业务分类">
        <el-option v-for="item in bizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
      </el-select>
    </section>

    <section v-loading="loading" class="market-grid">
      <article v-for="item in filteredItems" :key="item.id" class="market-card" @click="openDetail(item)">
        <div class="cover">
          <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" />
          <div v-else class="cover-fallback">FLOW</div>
        </div>
        <div class="market-card-body">
          <div class="market-card-head">
            <h2>{{ item.title }}</h2>
            <el-tag effect="plain">{{ marketTypeLabel(item.type) }}</el-tag>
          </div>
          <p>{{ item.description || '暂无说明' }}</p>
          <div class="market-meta">
            <span>{{ bizTypeName(item.bizTypeId) }}</span>
            <span>使用 {{ item.useCount || 0 }}</span>
            <span>评分 {{ item.rating ?? 0 }}</span>
          </div>
          <div class="market-tags">
            <el-tag v-for="tag in parseTags(item.tags)" :key="tag" round effect="plain">{{ tag }}</el-tag>
          </div>
          <div class="market-card-footer">
            <small>{{ formatTime(item.publishedAt) }}</small>
            <el-button type="success" round size="small" @click.stop="copyMarketItem(item)">复制到我的流程</el-button>
          </div>
        </div>
      </article>

      <el-empty v-if="!loading && filteredItems.length === 0" description="暂无模板市场数据" class="market-empty" />
    </section>

    <el-dialog v-model="detailVisible" title="模板详情" width="560px">
      <template v-if="selectedItem">
        <h2 class="detail-title">{{ selectedItem.title }}</h2>
        <p class="detail-desc">{{ selectedItem.description || '暂无说明' }}</p>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="业务分类">{{ bizTypeName(selectedItem.bizTypeId) }}</el-descriptions-item>
          <el-descriptions-item label="资源类型">{{ marketTypeLabel(selectedItem.type) }}</el-descriptions-item>
          <el-descriptions-item label="使用次数">{{ selectedItem.useCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ selectedItem.rating ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ parseTags(selectedItem.tags).join(' / ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上架时间">{{ formatTime(selectedItem.publishedAt) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button round @click="detailVisible = false">关闭</el-button>
        <el-button v-if="selectedItem" round type="success" @click="copyMarketItem(selectedItem)">复制到我的流程</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getBizTypes } from '@/api/bizType'
import { copyTemplateFromMarket, getTemplateMarketList } from '@/api/templateMarket'
import type { BizType, TemplateMarketItem } from '@/types/workflow'

const loading = ref(false)
const keyword = ref('')
const selectedBizTypeId = ref<number | null>(null)
const marketItems = ref<TemplateMarketItem[]>([])
const bizTypes = ref<BizType[]>([])
const selectedItem = ref<TemplateMarketItem | null>(null)
const detailVisible = ref(false)

const filteredItems = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return marketItems.value.filter((item) => {
    const matchKeyword = !key || item.title.toLowerCase().includes(key) || (item.description || '').toLowerCase().includes(key)
    const matchBizType = !selectedBizTypeId.value || item.bizTypeId === selectedBizTypeId.value
    return matchKeyword && matchBizType
  })
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [items, types] = await Promise.all([getTemplateMarketList(), getBizTypes()])
    marketItems.value = items
    bizTypes.value = types
  } finally {
    loading.value = false
  }
}

function openDetail(item: TemplateMarketItem) {
  selectedItem.value = item
  detailVisible.value = true
}

async function copyMarketItem(item: TemplateMarketItem) {
  const { value } = await ElMessageBox.prompt('请输入复制后的模板名称', '使用模板', {
    inputValue: `${item.title}-副本`,
    confirmButtonText: '复制',
    cancelButtonText: '取消'
  })
  await copyTemplateFromMarket(item.id, {
    newTemplateName: value
  })
  ElMessage.success('模板已复制到我的流程')
  detailVisible.value = false
  await loadData()
}

function bizTypeName(id?: number | null) {
  return bizTypes.value.find((item) => item.id === id)?.typeName || '未分类'
}

function marketTypeLabel(type?: string) {
  const map: Record<string, string> = {
    template: '流程模板',
    fragment: '流程片段'
  }
  return map[(type || '').toLowerCase()] || type || '-'
}

function parseTags(tags?: string) {
  if (!tags) return []
  try {
    const parsed = JSON.parse(tags)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return tags.split(',').map((item) => item.trim()).filter(Boolean)
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<style scoped>
.market-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.market-hero,
.market-filter,
.market-card {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}

.market-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 26px;
  border-radius: 24px;
}

.market-hero h1 {
  margin: 10px 0 6px;
  font-size: 30px;
}

.market-hero p,
.detail-desc {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.market-filter {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
}

.market-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  min-height: 280px;
}

.market-card {
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  transition: 0.18s ease;
}

.market-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 48px rgba(28, 55, 47, 0.12);
}

.cover {
  height: 132px;
  background: linear-gradient(135deg, #e8f7f1, #ffffff);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--primary-dark);
  font-size: 28px;
  font-weight: 800;
}

.market-card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
}

.market-card-head,
.market-card-footer,
.market-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.market-card-head {
  justify-content: space-between;
}

.market-card-head h2,
.detail-title {
  margin: 0;
  font-size: 18px;
}

.market-card-body p {
  min-height: 44px;
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.market-meta {
  flex-wrap: wrap;
  color: var(--muted);
  font-size: 13px;
}

.market-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 26px;
}

.market-card-footer {
  justify-content: space-between;
  padding-top: 4px;
}

.market-card-footer small {
  color: var(--muted);
}

.market-empty {
  grid-column: 1 / -1;
}

.detail-desc {
  margin: 10px 0 18px;
}

@media (max-width: 1100px) {
  .market-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .market-hero,
  .market-filter {
    align-items: flex-start;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .market-grid {
    grid-template-columns: 1fr;
  }
}
</style>
