import request from './request'
import type { ApiResponse, MarketCopyPayload, MarketPublishPayload, ProcessTemplate, TemplateMarketItem } from '@/types/workflow'

export async function getTemplateMarketList() {
  const response = await request.get<ApiResponse<TemplateMarketItem[]>>('/template-market')
  return response.data.data || []
}

export async function getTemplateMarketDetail(id: number) {
  const response = await request.get<ApiResponse<TemplateMarketItem>>(`/template-market/${id}`)
  return response.data.data
}

export async function publishTemplateToMarket(data: MarketPublishPayload) {
  const response = await request.post<ApiResponse<TemplateMarketItem>>('/template-market/publish-template', data)
  return response.data.data
}

export async function copyTemplateFromMarket(marketId: number, data: MarketCopyPayload) {
  const response = await request.post<ApiResponse<ProcessTemplate>>(`/template-market/${marketId}/copy`, data)
  return response.data.data
}
