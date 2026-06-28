import request from './request'
import type { BizType } from '@/types/workflow'

// 业务类型 API：获取业务类型列表

export async function getBizTypes() {
  return await request.get<BizType[]>('/api/biz-types') || []
}
