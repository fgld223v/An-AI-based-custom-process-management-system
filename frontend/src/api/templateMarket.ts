import request from './request'
import type { MarketCopyPayload, MarketPublishPayload, ProcessTemplate, TemplateMarketItem } from '@/types/workflow'

export async function getTemplateMarketList() {
  return await request.get<TemplateMarketItem[]>('/api/template-market') || []
}

export async function getTemplateMarketDetail(id: number) {
  return await request.get<TemplateMarketItem>(`/api/template-market/${id}`)
}

export async function publishTemplateToMarket(data: MarketPublishPayload) {
  return await request.post<TemplateMarketItem>('/api/template-market/publish-template', data)
}

export async function copyTemplateFromMarket(marketId: number, data: MarketCopyPayload) {
  return await request.post<ProcessTemplate>(`/api/template-market/${marketId}/copy`, data)
}

export async function withdrawFromMarket(marketId: number) {
  return await request.post<void>(`/api/template-market/${marketId}/withdraw`)
}
