import request from './request'

export interface StatisticsOverview {
  todayNewInstances: number
  pendingTaskCount: number
  totalInstances: number
  completionRate: number
  avgDurationHours: number
  anomalyCount: number
  statusDistribution: Record<string, number>
}

/** 统计概览 */
export function getStatisticsOverview() {
  return request.get<StatisticsOverview>('/api/statistics/overview')
}

/** 趋势数据 */
export function getStatisticsTrend(params: {
  start: string
  end: string
  granularity?: string
  mode?: string
}) {
  return request.get('/api/statistics/trend', { params })
}

/** 节点效率排名 */
export function getNodeEfficiency() {
  return request.get('/api/statistics/node-efficiency')
}
