// ADC 牙科诊所系统前端 API 封装
// token 存 localStorage，所有请求自动带 Authorization: Bearer <token>
// 默认使用相对路径 /api：开发环境由 vite 代理到 127.0.0.1:8080，
// Docker 环境由 nginx 代理到 backend 容器；也可用 VITE_API_BASE 指定绝对地址。
const TOKEN_KEY = 'adc_demo_token'
export const apiBase = import.meta.env.VITE_API_BASE || ''

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

const AUTH_PATHS = ['/api/auth/login', '/api/auth/register-patient', '/api/auth/me']

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`${apiBase}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  // 登录/注册接口的 401 是「账号或密码错误」，不应触发全局登出，也不能
  // 覆盖后端给出的真实原因（否则用户只看到"未登录或登录已过期"）。
  const isAuthCall = AUTH_PATHS.includes(path)
  if (res.status === 401 && !isAuthCall) {
    clearToken()
    window.dispatchEvent(new Event('adc:unauthorized'))
    throw new ApiError(401, '未登录或登录已过期')
  }
  if (res.status === 403) {
    throw new ApiError(403, '请求被拒绝（403）。若刚从其他地址打开过本系统，请强制刷新页面（Ctrl+F5）后重试')
  }
  let data = null
  try { data = await res.json() } catch { /* 204 等无响应体 */ }
  if (!res.ok) {
    throw new ApiError(res.status, (data && data.message) || `请求失败（${res.status}）`)
  }
  return data
}

// ---------- 业务接口 ----------
export const auth = {
  login: (loginName, password) =>
    api('/api/auth/login', { method: 'POST', body: { loginName, password } }),
  register: (form) =>
    api('/api/auth/register-patient', { method: 'POST', body: form }),
  me: () => api('/api/auth/me')
}

export const patients = {
  list: () => api('/api/patients')
}

export const appointments = {
  list: () => api('/api/appointments'),
  shifts: () => api('/api/appointments/shifts'),
  create: (form) => api('/api/appointments', { method: 'POST', body: form }),
  cancel: (id) => api(`/api/appointments/${id}/cancel`, { method: 'POST' }),
  reschedule: (id, form) => api(`/api/appointments/${id}`, { method: 'PUT', body: form })
}

export const shifts = {
  availableSlots: (dentistId, date) =>
    api(`/api/shifts/available-slots?dentistId=${dentistId}&date=${date}`)
}

export const shiftRequests = {
  list: () => api('/api/shift-requests'),
  detail: (id) => api(`/api/shift-requests/${id}`),
  submit: (form) => api('/api/shift-requests', { method: 'POST', body: form }),
  review: (id, form) => api(`/api/shift-requests/${id}/review`, { method: 'PUT', body: form })
}

export const encounters = {
  list: () => api('/api/encounters'),
  detail: (id) => api(`/api/encounters/${id}`),
  checkIn: (appointmentId) => api(`/api/encounters/appointments/${appointmentId}/check-in`, { method: 'POST' }),
  update: (id, form) => api(`/api/encounters/${id}`, { method: 'PUT', body: form }),
  addTreatmentLine: (id, form) => api(`/api/encounters/${id}/treatment-lines`, { method: 'POST', body: form }),
  removeTreatmentLine: (id, lineId) =>
    api(`/api/encounters/${id}/treatment-lines/${lineId}`, { method: 'DELETE' })
}

export const invoices = {
  list: () => api('/api/invoices'),
  detail: (id) => api(`/api/invoices/${id}`),
  generate: (encounterId) => api(`/api/invoices/encounters/${encounterId}`, { method: 'POST' }),
  confirm: (id) => api(`/api/invoices/${id}/confirm`, { method: 'PUT' }),
  pay: (id, form) => api(`/api/invoices/${id}/payments`, { method: 'POST', body: form }),
  void: (id) => api(`/api/invoices/${id}/void`, { method: 'PUT' })
}

export const inventory = {
  stock: () => api('/api/inventory'),
  lowStock: () => api('/api/inventory/low-stock'),
  movements: () => api('/api/inventory/movements'),
  createMovement: (form) => api('/api/inventory/movements', { method: 'POST', body: form })
}

// ---------- 患者自助门户 ----------
export const patientPortal = {
  slots: (days = 7) => api(`/api/portal/patient/slots?days=${days}`),
  appointments: () => api('/api/portal/patient/appointments'),
  book: (form) => api('/api/portal/patient/appointments', { method: 'POST', body: form }),
  reschedule: (id, form) => api(`/api/portal/patient/appointments/${id}/reschedule`, { method: 'POST', body: form }),
  cancel: (id) => api(`/api/portal/patient/appointments/${id}/cancel`, { method: 'POST' }),
  profile: () => api('/api/portal/patient/profile'),
  history: () => api('/api/portal/patient/history'),
  invoices: () => api('/api/portal/patient/invoices')
}

// ---------- 医生工作台 ----------
export const dentistPortal = {
  roster: (weekOf) => api(weekOf ? `/api/portal/dentist/roster?weekOf=${weekOf}` : '/api/portal/dentist/roster'),
  dayAppointments: (date) => api(date ? `/api/portal/dentist/appointments?date=${date}` : '/api/portal/dentist/appointments'),
  patientSummary: (id) => api(`/api/portal/dentist/patients/${id}/summary`),
  performance: (month) => api(month ? `/api/portal/dentist/performance?month=${month}` : '/api/portal/dentist/performance'),
  myShiftRequests: () => api('/api/portal/dentist/shift-requests')
}

// ---------- 前台工作台 ----------
export const receptionPortal = {
  appointments: (date) => api(date ? `/api/portal/reception/appointments?date=${date}` : '/api/portal/reception/appointments'),
  checkIn: (id) => api(`/api/portal/reception/appointments/${id}/check-in`, { method: 'POST' }),
  reminders: () => api('/api/portal/reception/reminders'),
  createQr: (form) => api('/api/portal/reception/payments/qr', { method: 'POST', body: form }),
  qrStatus: (id) => api(`/api/portal/reception/payments/${id}/status`),
  supplies: () => api('/api/portal/reception/supplies')
}

// ---------- 管理层经营面板 ----------
export const managerPortal = {
  dashboard: (from, to) => api(`/api/portal/manager/dashboard?from=${from}&to=${to}`),
  summary: (from, to) => api(`/api/portal/manager/summary?from=${from}&to=${to}`),
  staffOnDuty: (date) => api(`/api/portal/manager/staff-on-duty?date=${date}`),
  pendingRequests: (status) => api(`/api/portal/manager/shift-requests?status=${status || 'SUBMITTED'}`),
  candidates: (id) => api(`/api/portal/manager/shift-requests/${id}/candidates`),
  performance: (month) => api(month ? `/api/portal/manager/performance?month=${month}` : '/api/portal/manager/performance')
}

export const performance = {
  list: () => api('/api/performance'),
  recalculate: (form) => api('/api/performance/recalculate', { method: 'POST', body: form }),
  review: (id, form) => api(`/api/performance/${id}/review`, { method: 'PUT', body: form })
}

// ---------- 通用工具 ----------
// 本地时区的今天（YYYY-MM-DD）。勿用 toISOString().slice(0,10)：那是 UTC 日期，
// 中国用户（UTC+8）在早上 8 点前会得到前一天，且与后端按本地日期存的数据口径不一致。
export function todayLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
// 本地时区的当前月（YYYY-MM）
export function monthLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}`
}
export function fmtTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export function fmtDate(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function toLocalInput(v) {
  const d = v ? new Date(v) : new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

export function money(v) {
  if (v === null || v === undefined || v === '') return '—'
  return '¥' + Number(v).toFixed(2)
}

export const ROLE_LABEL = {
  MANAGER: '管理层',
  RECEPTION: '前台',
  DENTIST: '医生',
  PATIENT: '患者'
}
