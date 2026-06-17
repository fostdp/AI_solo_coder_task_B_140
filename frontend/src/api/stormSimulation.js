import request from './request'

export function simulateStorm(shipId, params) {
  return request.post(`/storm-simulation/simulate/${shipId}`, params)
}

export function batchSimulateStorm(params) {
  return request.post('/storm-simulation/batch-simulate', params)
}

export function getLatestSimulation(shipId) {
  return request.get(`/storm-simulation/ship/${shipId}/latest`)
}

export function getSimulationHistory(shipId, limit = 20) {
  return request.get(`/storm-simulation/ship/${shipId}/history?limit=${limit}`)
}

export function getPublicSimulations() {
  return request.get('/storm-simulation/public')
}

export function getSimulationById(id) {
  return request.get(`/storm-simulation/${id}`)
}

export function deleteSimulation(id) {
  return request.delete(`/storm-simulation/${id}`)
}
