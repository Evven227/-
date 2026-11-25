<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api, auth, clearToken, getToken, setToken, ROLE_LABEL } from './api.js'
import AppointmentsView from './views/AppointmentsView.vue'
import PatientsView from './views/PatientsView.vue'
import ShiftRequestsView from './views/ShiftRequestsView.vue'
import EncountersView from './views/EncountersView.vue'
import InvoicesView from './views/InvoicesView.vue'
import InventoryView from './views/InventoryView.vue'
import PerformanceView from './views/PerformanceView.vue'
import PatientBookingView from './views/patient/PatientBookingView.vue'
import PatientProfileView from './views/patient/PatientProfileView.vue'
import PatientBillsView from './views/patient/PatientBillsView.vue'
import DentistScheduleView from './views/dentist/DentistScheduleView.vue'
import DentistPerformanceView from './views/dentist/DentistPerformanceView.vue'
import ReceptionDeskView from './views/reception/ReceptionDeskView.vue'
import ReceptionPayView from './views/reception/ReceptionPayView.vue'
import ManagerDashboardView from './views/manager/ManagerDashboardView.vue'
import ManagerApprovalView from './views/manager/ManagerApprovalView.vue'

const account = ref(null)
const view = ref('appointments')
const error = ref('')
const notice = ref('')
const loginName = ref('hq_manager')
const password = ref('AdcDemo@2026!')
const registerMode = ref(false)
const registerForm = ref({ loginName: '', password: 'AdcDemo@2026!', name: '', phone: '', email: '' })

// 登录页角色选择（仅作为选择演示账号的入口，实际角色以后端 /me 返回为准）
const selectedRole = ref('')
const ROLE_CARDS = [
  { key: 'PATIENT', label: '患者', badge: '患', desc: '自助预约 · 我的就诊与账单' },
  { key: 'DENTIST', label: '医生', badge: '医', desc: '我的班次 · 调班 · 就诊 · 绩效' },
  { key: 'RECEPTION', label: '前台', badge: '前', desc: '代预约 · 签到 · 收款 · 库存' },
  { key: 'MANAGER', label: '管理者', badge: '管', desc: '诊所或集团全局管理' }
]
const DEMO_ACCOUNTS = {
  PATIENT: ['patient_lin', 'patient_chen', 'ewen'],
  DENTIST: ['dentist_zhou', 'dentist_wu', 'dentist_liu', 'dentist_sun', 'dentist_zheng'],
  RECEPTION: ['reception_a', 'reception_b'],
  MANAGER: ['manager_a', 'manager_b', 'hq_manager']
}
function pickRole(key) { selectedRole.value = key; loginName.value = '' }
function quickLogin(name) { loginName.value = name; password.value = 'AdcDemo@2026!' }

const roles = computed(() => account.value?.roles || [])
const isPatient = computed(() => roles.value.includes('PATIENT'))
const isStaff = computed(() => roles.value.some(r => r !== 'PATIENT'))

const navItems = computed(() => {
  const r = roles.value
  const items = []

  // ---- 患者：自助门户（预约 / 资料 / 账单）----
  if (r.includes('PATIENT')) {
    items.push({ key: 'p-booking', label: '预约就诊' })
    items.push({ key: 'p-profile', label: '我的资料' })
    items.push({ key: 'p-bills', label: '我的账单' })
  }

  // ---- 医生：工作台（排班 / 病历处方 / 调班 / 绩效）----
  if (r.includes('DENTIST')) {
    items.push({ key: 'd-schedule', label: '本周排班与当日预约' })
    items.push({ key: 'd-encounters', label: '病历与处方' })
    items.push({ key: 'd-shift', label: '调班申请' })
    items.push({ key: 'd-performance', label: '我的绩效' })
  }

  // ---- 前台：预约台 / 收款 / 耗材 / 自己的调班 ----
  if (r.includes('RECEPTION')) {
    items.push({ key: 'r-desk', label: '预约与签到' })
    items.push({ key: 'r-pay', label: '收款二维码' })
    items.push({ key: 'inventory', label: '耗材管理' })
    items.push({ key: 'd-shift', label: '调班申请' })
  }

  // ---- 管理层：经营看板 / 调班审核 / 绩效 ----
  if (r.includes('MANAGER')) {
    items.push({ key: 'm-dashboard', label: '营业看板' })
    items.push({ key: 'm-approval', label: '调班审批' })
    items.push({ key: 'performance', label: '绩效管理' })
    items.push({ key: 'appointments', label: '预约查询' })
    items.push({ key: 'invoices', label: '账单查询' })
    items.push({ key: 'inventory', label: '耗材管理' })
  }
  // 去重（同一 key 可能出现两次）
  const seen = new Set()
  return items.filter(i => !seen.has(i.key) && seen.add(i.key))
})

// 角色切换后，若当前视图不属于该角色导航范围（例如从管理者退出后以患者登录，
// 残留 view='performance'），自动回落到该角色的第一个页面，避免出现空白主区域。
watch(navItems, (items) => {
  if (items.length && !items.some(i => i.key === view.value)) {
    view.value = items[0].key
  }
}, { immediate: true })

onMounted(async () => {
  if (!getToken()) return
  try {
    account.value = await auth.me()
  } catch {
    clearToken()
  }
})

window.addEventListener('adc:unauthorized', () => {
  account.value = null
  error.value = '登录已过期，请重新登录'
})

async function login() {
  error.value = ''
  try {
    const data = await auth.login(loginName.value, password.value)
    setToken(data.token)
    account.value = await auth.me()
    notice.value = ''
  } catch (e) {
    error.value = e.message
  }
}

async function registerPatient() {
  error.value = ''
  try {
    const data = await auth.register(registerForm.value)
    setToken(data.token)
    account.value = await auth.me()
    notice.value = '注册成功，已自动登录'
    registerMode.value = false
  } catch (e) {
    error.value = e.message
  }
}

function logout() {
  clearToken()
  account.value = null
  error.value = ''
  notice.value = ''
  // 回到角色选择页，并清空登录/注册表单，避免带着上一次的账号密码再次提交
  selectedRole.value = ''
  registerMode.value = false
  loginName.value = ''
  password.value = ''
  registerForm.value = { loginName: '', password: '', name: '', phone: '', email: '' }
}
</script>

<template>
  <main class="page">
    <!-- 登录 / 注册 -->
    <section v-if="!account" class="card login-card">
      <div class="brand">
        <span class="tooth-badge">
          <svg viewBox="0 0 64 80" class="tooth" aria-label="牙齿标志">
            <defs>
              <linearGradient id="adcTooth" x1="0" y1="0" x2="0.3" y2="1">
                <stop offset="0%" stop-color="#ffffff" />
                <stop offset="52%" stop-color="#f8fcff" />
                <stop offset="100%" stop-color="#e4f1fa" />
              </linearGradient>
            </defs>
            <path d="M32 5C19.8 5 10.5 14.1 10.5 26.6c0 7.1 2.9 12.2 6.9 17.1 2.2 5.6 4.1 16.6 7.1 23.6 1.3 3.1 2.5 2.8 3.3.8.7-1.9.8-5.7.8-5.7h7.4s.1 3.8.8 5.7c.8 2 2 2.3 3.3-.8 3-7 4.9-18 7.1-23.6 4-4.9 6.9-10 6.9-17.1C53.5 14.1 44.2 5 32 5z"
                  fill="url(#adcTooth)" stroke="#c7dff0" stroke-width="1.6" stroke-linejoin="round" />
            <ellipse cx="24.5" cy="21" rx="4.6" ry="7" fill="#ffffff" opacity=".9" transform="rotate(-18 24.5 21)" />
            <path d="M17 33c2.5 2.2 5.6 3.4 9 3.4" fill="none" stroke="#dcecf8" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </span>
        <div class="brand-text">
          <h1>ADC 牙科诊所管理系统</h1>
          <p class="muted">Andrew's Dental Chain · 课程演示环境 · Spring Boot + Vue 3 + PostgreSQL</p>
        </div>
      </div>
      <p class="muted role-hint">请先选择您的角色</p>

      <!-- 角色选择 -->
      <div v-if="!selectedRole && !registerMode" class="role-cards">
        <button v-for="r in ROLE_CARDS" :key="r.key" class="role-card" @click="pickRole(r.key)">
          <span class="role-badge">{{ r.badge }}</span>
          <span class="role-meta">
            <strong>{{ r.label }}</strong>
            <span>{{ r.desc }}</span>
          </span>
        </button>
      </div>
      <button v-if="!selectedRole && !registerMode" class="link" @click="pickRole('PATIENT')">直接输入账号登录</button>

      <!-- 角色下登录 -->
      <template v-if="selectedRole && !registerMode">
        <button class="link" @click="selectedRole = ''">← 重新选择角色</button>
        <p class="muted">已选角色：{{ (ROLE_CARDS.find(r => r.key === selectedRole) || {}).label }} · 点击下方演示账号可一键填入</p>
        <div class="demo-row">
          <button v-for="n in DEMO_ACCOUNTS[selectedRole]" :key="n" class="demo-btn" @click="quickLogin(n)">{{ n }}</button>
        </div>
        <label>登录账号<input v-model="loginName" autocomplete="username" /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
        <button @click="login">登录</button>
        <p v-if="error" class="error">{{ error }}</p>
        <button v-if="selectedRole === 'PATIENT'" class="link" @click="registerMode = true">注册新患者账号</button>
      </template>

      <!-- 注册 -->
      <template v-if="registerMode">
        <button class="link" @click="selectedRole = ''; registerMode = false">← 返回登录</button>
        <h2>注册新患者</h2>
        <label>登录账号<input v-model="registerForm.loginName" /></label>
        <label>密码<input v-model="registerForm.password" type="password" /></label>
        <label>姓名<input v-model="registerForm.name" /></label>
        <label>出生日期<input v-model="registerForm.birthDate" type="date" /></label>
        <label>联系电话<input v-model="registerForm.phone" /></label>
        <label>邮箱（可选）<input v-model="registerForm.email" /></label>
        <button @click="registerPatient">注册并进入系统</button>
        <p v-if="error" class="error">{{ error }}</p>
      </template>
    </section>

    <!-- 主界面 -->
    <section v-else class="shell">
      <header>
        <div class="head-left">
          <span class="mini-tooth">
            <svg viewBox="0 0 64 80" class="tooth" aria-label="牙齿标志">
              <defs>
                <linearGradient id="adcToothSmall" x1="0" y1="0" x2="0.3" y2="1">
                  <stop offset="0%" stop-color="#ffffff" />
                  <stop offset="52%" stop-color="#f8fcff" />
                  <stop offset="100%" stop-color="#dff0fa" />
                </linearGradient>
              </defs>
              <path d="M32 5C19.8 5 10.5 14.1 10.5 26.6c0 7.1 2.9 12.2 6.9 17.1 2.2 5.6 4.1 16.6 7.1 23.6 1.3 3.1 2.5 2.8 3.3.8.7-1.9.8-5.7.8-5.7h7.4s.1 3.8.8 5.7c.8 2 2 2.3 3.3-.8 3-7 4.9-18 7.1-23.6 4-4.9 6.9-10 6.9-17.1C53.5 14.1 44.2 5 32 5z"
                    fill="url(#adcToothSmall)" stroke="#bcd9ee" stroke-width="2" stroke-linejoin="round" />
              <ellipse cx="24.5" cy="21" rx="4.6" ry="7" fill="#ffffff" opacity=".9" transform="rotate(-18 24.5 21)" />
            </svg>
          </span>
          <div>
            <h1>ADC 牙科诊所管理系统</h1>
            <span>{{ account.loginName }} · {{ account.roles.map(r => ROLE_LABEL[r] || r).join('、') }}</span>
          </div>
        </div>
        <button class="secondary" @click="logout">退出登录</button>
      </header>
      <p v-if="notice" class="notice">{{ notice }}</p>
      <p v-if="error" class="error">{{ error }}</p>

      <nav class="tabs">
        <button v-for="item in navItems" :key="item.key"
                :class="['tab', { active: view === item.key }]"
                @click="view = item.key">{{ item.label }}</button>
      </nav>

      <PatientBookingView v-if="view === 'p-booking'" :account="account" />
      <PatientProfileView v-else-if="view === 'p-profile'" :account="account" />
      <PatientBillsView v-else-if="view === 'p-bills'" :account="account" />
      <DentistScheduleView v-else-if="view === 'd-schedule'" :account="account" />
      <EncountersView v-else-if="view === 'd-encounters'" :account="account" />
      <ShiftRequestsView v-else-if="view === 'd-shift'" :account="account" />
      <DentistPerformanceView v-else-if="view === 'd-performance'" :account="account" />
      <ReceptionDeskView v-else-if="view === 'r-desk'" :account="account" />
      <ReceptionPayView v-else-if="view === 'r-pay'" :account="account" />
      <ManagerDashboardView v-else-if="view === 'm-dashboard'" :account="account" />
      <ManagerApprovalView v-else-if="view === 'm-approval'" :account="account" />
      <AppointmentsView v-else-if="view === 'appointments'" :account="account" />
      <PatientsView v-else-if="view === 'patients'" :account="account" />
      <ShiftRequestsView v-else-if="view === 'shift-requests'" :account="account" />
      <EncountersView v-else-if="view === 'encounters'" :account="account" />
      <InvoicesView v-else-if="view === 'invoices'" :account="account" />
      <InventoryView v-else-if="view === 'inventory'" :account="account" />
      <PerformanceView v-else-if="view === 'performance'" :account="account" />
    </section>
  </main>
</template>

<style scoped>
.login-card{position:relative;overflow:hidden}
.login-card::before{
  content:"";position:absolute;left:-70px;bottom:-90px;width:230px;height:230px;border-radius:50%;
  background:radial-gradient(circle,rgba(2,132,199,.10),transparent 70%);pointer-events:none;
}
.login-card::after{
  content:"";position:absolute;right:-60px;top:-70px;width:190px;height:190px;border-radius:50%;
  background:radial-gradient(circle,rgba(13,148,136,.10),transparent 70%);pointer-events:none;
}

/* 品牌区：牙齿标志 + 标题 */
.brand{display:flex;align-items:center;gap:16px;position:relative;z-index:1}
.tooth-badge{
  flex:0 0 auto;width:66px;height:66px;border-radius:20px;display:flex;align-items:center;justify-content:center;
  background:linear-gradient(150deg,#eaf7ff 0%,#e6fbf6 100%);
  border:1px solid #dbeeff;
  box-shadow:0 8px 20px rgba(2,132,199,.14),inset 0 1px 0 #fff;
}
.tooth-badge .tooth{width:38px;height:48px;filter:drop-shadow(0 3px 6px rgba(13,60,97,.14))}
.brand-text h1{margin:0 0 4px;font-size:23px;line-height:1.25}
.brand-text .muted{margin:0;font-size:12.5px}
.role-hint{margin:16px 0 0;font-size:13.5px;font-weight:600;color:var(--brand-dark);position:relative;z-index:1}

.role-cards{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin:14px 0 18px;position:relative;z-index:1}
.role-card{
  display:flex;flex-direction:row;align-items:center;gap:12px;padding:14px 16px;
  border:1px solid #dbe9f6;border-radius:14px;
  background:linear-gradient(160deg,#ffffff 0%,#f6fbff 100%);
  box-shadow:none;margin:0;text-align:left;transition:border-color .15s,box-shadow .15s,transform .12s;
}
.role-card:hover{border-color:#8ecbf0;background:linear-gradient(160deg,#f4fbff 0%,#eaf7ff 100%);box-shadow:0 10px 22px rgba(2,132,199,.14);transform:translateY(-2px)}
.role-badge{
  flex:0 0 auto;width:38px;height:38px;border-radius:12px;display:flex;align-items:center;justify-content:center;
  font-size:17px;font-weight:700;color:#fff;
  background:linear-gradient(135deg,#38bdf8 0%,#0d9488 100%);
  box-shadow:0 5px 12px rgba(2,132,199,.28);
}
.role-meta{display:flex;flex-direction:column;gap:3px;min-width:0}
.role-meta strong{font-size:16px;color:#123a5c}
.role-meta span{font-size:12px;color:#5b7288;line-height:1.35}

.demo-row{display:flex;flex-wrap:wrap;gap:8px;margin:10px 0;position:relative;z-index:1}
.demo-btn{
  padding:6px 12px;border:1px solid #cfe2f2;background:#fff;color:#334;border-radius:8px;font-size:13px;margin:0;cursor:pointer;box-shadow:none;
}
.demo-btn:hover{border-color:#38bdf8;color:var(--brand-dark);background:#f3fbff;transform:none;filter:none;box-shadow:0 4px 10px rgba(2,132,199,.12)}

/* 主界面顶栏标志 */
.head-left{display:flex;align-items:center;gap:14px}
.mini-tooth{
  flex:0 0 auto;width:46px;height:46px;border-radius:14px;display:flex;align-items:center;justify-content:center;
  background:linear-gradient(150deg,#eaf7ff 0%,#e8fbf6 100%);border:1px solid #dbeeff;
  box-shadow:0 6px 14px rgba(2,132,199,.12),inset 0 1px 0 #fff;
}
.mini-tooth .tooth{width:26px;height:33px}
</style>
