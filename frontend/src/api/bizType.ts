import request from './request'
import type { ApiResponse, BizType } from '@/types/workflow'

export async function getBizTypes() {
  const response = await request.get<ApiResponse<BizType[]>>('/biz-types')
  return response.data.data || []
}
