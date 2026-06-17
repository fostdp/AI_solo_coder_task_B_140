import request from './request'

export function compareShips(params) {
  return request.post('/ship-comparison/compare', params)
}

export function getComparisonHistory(limit = 10) {
  return request.get(`/ship-comparison/history?limit=${limit}`)
}

export function getAvailableShips() {
  return request.get('/ship-comparison/ships')
}

export function getComparisonById(id) {
  return request.get(`/ship-comparison/${id}`)
}

export function deleteComparison(id) {
  return request.delete(`/ship-comparison/${id}`)
}
