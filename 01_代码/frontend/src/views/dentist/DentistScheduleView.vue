<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { dentistPortal, encounters, fmtDate, fmtTime, todayLocal } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const roster = ref(null)
const appts = ref([])
const error = ref('')
const dateInput = ref(todayLocal())
const summary = ref(null)
const summaryOpen = ref(false)

const weekDays = computed(() => {
  if (!roster.value) return []
  const byDate = new Map()
  for (const s of roster.value.shifts) {
    byDate.set(String(s.shift_date).slice(0, 10), s)
  }
  const out = []
  const start = new Date(roster.value.weekStart + 'T00:00:00')
  const p = (n) => String(n).padStart(2, '0')
  for (let i = 0; i < 7; i++) {
    const d = new Date(start.getTime() + i * 86400000)
    // 用本地时间拼日期，避免 toISOString() 的 UTC 转换导致差一天
    const iso = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
    out.push({ iso, shift: byDate.get(iso) || null })
  }
  return out
})

onMounted(load)
onMounted(() => document.addEventListener('visibilitychange', onVisible))
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisible))

// 患者在前台/患者门户改约后，医生切回本页即看到最新预约
function onVisible() {
  if (document.visibilityState === 'visible') loadDay()
}

async function load() {
  error.value = ''
  try {
    roster.value = await dentistPortal.roster()
    await loadDay()
  } catch (e) {
    error.value = e.message
  }
}

async function loadDay() {
  try {
    appts.value = await dentistPortal.dayAppointments(dateInput.value)
  } catch (e) {
    error.value = e.message
  }
}

function pickDate(iso) {
  dateInput.value = iso
  loadDay()
}

async function viewPatient(patientId) {
  try {
    summary.value = await dentistPortal.patientSummary(patientId)
    summaryOpen.value = true
  } catch (e) {
    error.value = e.message
  }
}

async function checkIn(appointmentId) {
  try {
    await encounters.checkIn(appointmentId)
    await loadDay()
  } catch (e) {
    error.value = e.message
  }
}

function statusText(s) {
  return { BOOKED: '已预约', ARRIVED: '已到诊', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '爽约' }[s] || s
}
function sourceTag(s) {
  return s.source === 'SUBSTITUTE' ? (s.substitute_from === 'CROSS_CLINIC' ? '跨店顶班' : '同店顶班') : ''
}
</script>

<template>
  <section>
    <h2>本周排班与当日预约</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="roster" class="week">
      <button v-for="d in weekDays" :key="d.iso"
              :class="['day', { on: d.shift, today: d.iso === roster.today, selected: d.iso === dateInput }]"
              @click="pickDate(d.iso)">
        <div class="d1">{{ d.iso.slice(5) }}</div>
        <div v-if="d.shift" class="d2">
          {{ d.shift.clinic_name }}
          <span v-if="sourceTag(d.shift)" class="tag cover">{{ sourceTag(d.shift) }}</span>
        </div>
        <div v-else class="d2 rest">休息</div>
        <div v-if="d.shift" class="d3">同事：{{ d.shift.colleagues }}</div>
        <div v-if="d.shift" class="d3">前台：{{ d.shift.reception || '—' }}</div>
      </button>
    </div>

    <div class="row">
      <h3>{{ dateInput }} 的预约（{{ appts.length }} 位患者）</h3>
      <label class="inline">日期<input type="date" v-model="dateInput" @change="loadDay" /></label>
    </div>

    <table v-if="appts.length">
      <thead>
        <tr>
          <th>时间</th><th>预约号</th><th>患者</th><th>联系电话</th>
          <th>过敏/特殊备注</th><th>状态</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in appts" :key="a.appointment_id">
          <td>{{ fmtTime(a.starts_at) }}<br /><span class="muted">{{ a.slot_name }}</span></td>
          <td>{{ a.booking_no || a.appointment_id }}</td>
          <td>
            <button class="link" @click="viewPatient(a.patient_id)">{{ a.patient_name }}</button>
            <span class="muted"> / {{ a.birth_date || '—' }}</span>
          </td>
          <td>{{ a.phone }}</td>
          <td>
            <span class="muted">{{ a.allergies }}</span>
            <span v-if="a.special_needs" class="tag warn">备注：{{ a.special_needs }}</span>
          </td>
          <td>{{ statusText(a.status) }}</td>
          <td>
            <button v-if="a.status === 'BOOKED'" class="mini" @click="checkIn(a.appointment_id)">签到</button>
            <span v-else-if="a.encounter_count" class="tag ok">已建档</span>
            <span v-else class="muted">—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">当日没有预约记录（可能当天休息或暂无患者预约）。</p>

    <div v-if="summaryOpen && summary" class="panel">
      <div class="row">
        <h3>患者档案摘要 · {{ summary.profile.name }}</h3>
        <button class="link" @click="summaryOpen = false">收起</button>
      </div>
      <p class="muted">
        病历号 {{ summary.profile.master_no }} · 出生 {{ summary.profile.birth_date || '—' }} ·
        电话 {{ summary.profile.phone }} · 过敏评估：{{ summary.profile.allergy_assessment }}
      </p>
      <p v-if="summary.allergies.length" class="tag warn">
        过敏原：{{ summary.allergies.map(a => `${a.allergen}(${a.reaction})`).join('、') }}
      </p>
      <h4>既往治疗</h4>
      <table v-if="summary.history.length">
        <thead><tr><th>日期</th><th>主诉</th><th>诊断</th><th>项目</th><th>数量</th><th>单价</th></tr></thead>
        <tbody>
          <tr v-for="(h, i) in summary.history" :key="i">
            <td>{{ fmtDate(h.arrived_at) }}</td>
            <td>{{ h.chief_complaint || '—' }}</td>
            <td>{{ h.diagnosis || '—' }}</td>
            <td>{{ h.name_snapshot || '—' }}</td>
            <td>{{ h.quantity || '—' }}</td>
            <td>{{ h.unit_price_snapshot ? '¥' + Number(h.unit_price_snapshot).toFixed(2) : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">无既往治疗记录。</p>
    </div>
  </section>
</template>

<style scoped>
h2 { margin-bottom: 10px; }
h3 { margin: 18px 0 8px; }
.row { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.inline { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 13px; }
.inline input { width: auto; }
.week { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; margin-bottom: 6px; }
.day {
  border: 1px solid #d7dfeb; border-radius: 10px; padding: 10px 8px; text-align: left;
  background: #fff; cursor: pointer; min-height: 92px;
  color: #172033; /* 覆盖全局 button 的白色字，避免白底白字 */
}
.day:hover { border-color: #2563eb; }
.day.on { background: #f0f7ff; }
.day.today { box-shadow: inset 0 0 0 2px #93c5fd; }
.day.selected { border-color: #2563eb; }
.day.rest { color: #94a3b8; }
.d1 { font-weight: 600; font-size: 13px; }
.d2 { font-size: 12px; margin: 4px 0; }
.d3 { font-size: 11px; color: #6b7a90; }
.tag { display: inline-block; padding: 1px 6px; border-radius: 8px; font-size: 11px; margin-left: 4px; }
.tag.cover { background: #fef9c3; color: #854d0e; }
.tag.warn { background: #fee2e2; color: #991b1b; }
.tag.ok { background: #dcfce7; color: #166534; }
.panel { margin-top: 16px; border-top: 1px solid #e6eaf0; padding-top: 12px; }
.panel h4 { margin: 10px 0 6px; }
button.link { border: none; background: none; padding: 0; cursor: pointer; }
button.mini { padding: 3px 10px; font-size: 12px; }
button.link { color: #2563eb; }
</style>
