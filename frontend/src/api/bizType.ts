import request from './request'
import type { BizType } from '@/types/workflow'

export async function getBizTypes() {
  return await request.get<BizType[]>('/api/biz-types') || []
}
