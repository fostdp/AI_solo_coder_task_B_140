import request from './request'

export function createLoadingSession(params) {
  return request.post('/virtual-loading/session', params)
}

export function executeLoadingAction(params) {
  return request.post('/virtual-loading/action', params)
}

export function getLoadingSession(id) {
  return request.get(`/virtual-loading/session/${id}`)
}

export function getActiveSessions(shipId) {
  return request.get(`/virtual-loading/ship/${shipId}/active`)
}

export function getPublicSessions(page = 0, size = 10) {
  return request.get(`/virtual-loading/public?page=${page}&size=${size}`)
}

export function getUserSessions(userId) {
  return request.get(`/virtual-loading/user/${userId}`)
}

export function cloneLoadingSession(id, newName) {
  return request.post(`/virtual-loading/session/${id}/clone`, { newName })
}

export function closeLoadingSession(id) {
  return request.delete(`/virtual-loading/session/${id}`)
}
