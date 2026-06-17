import request from './request'

export function getCargoTypes() {
  return request.get('/cargo/types')
}

export function getCargoTypeByCode(code) {
  return request.get(`/cargo/types/${code}`)
}

export function getCargoHolds(shipId) {
  return request.get(`/cargo/holds/ship/${shipId}`)
}

export function getCargoLoadings(shipId) {
  return request.get(`/cargo/loading/ship/${shipId}`)
}

export function getLoadingSummary(shipId) {
  return request.get(`/cargo/loading/ship/${shipId}/summary`)
}

export function createCargoLoading(data) {
  return request.post('/cargo/loading', data)
}

export function deleteCargoLoading(id) {
  return request.delete(`/cargo/loading/${id}`)
}

export function clearNonOptimizedLoadings(shipId) {
  return request.post(`/cargo/loading/ship/${shipId}/clear`)
}
