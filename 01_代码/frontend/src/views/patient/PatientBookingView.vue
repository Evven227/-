<script setup>
import { computed, onMounted, ref } from 'vue'
import { patientPortal, fmtDate, fmtTime } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })
const emit = defineEmits(['notify'])

const slots = ref([])
const list = ref([])
const error = ref('')
const notice = ref('')
const busy = ref(false)
const rescheduling = ref(null)   // 正在改约的预约 id
const pickedDate = ref('')       // 第二步：已选定的日期

// 第一步：把 7 天按日期汇总，先让患者选日期
const dayOptions = computed(() => {
  const map = new Map()
  for (const s of slots.value) {
    const d = String(s.slot_date).slice(0, 10)
    if (!map.has(d)) map.set(d, { date: d, total: 0, free: 0, clinics: new Set(), slots: 0 })
    const o = map.get(d)
    o.slots += 1
    o.total += s.capacity
    o.free += s.remaining
    o.clinics.add(s.clinic_name)
  }
  return [...map.values()]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map(o => ({ ...o, clinics: [...o.clinics] }))
})

// 第二步：所选当天的时段
const daySlots = computed(() => {
  if (!pickedDate.value) return []
  return slots.value
    .filter(s => String(s.slot_date).slice(0, 10) === pickedDate.value)
    .sort((a, b) => a.clinic_name.localeCompare(b.clinic_name) || a.slot_code.localeCompare(b.slot_code))
})

// 只展示仍在有效期内的预约（已预约/已到诊）；已取消、已完成、爽约不占用"当前预约"
const activeList = computed(() =>
  list.value.filter(a => ['BOOKED', 'ARRIVED'].includes(a.status)))
const activeCount = computed(() => activeList.value.length)

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [s, a] = await Promise.all([patientPortal.slots(7), patientPortal.appointments()])
    slots.value = s
    list.value = a
  } catch (e) {
    error.value = e.message
  }
}

function pickDay(d) {
  pickedDate.value = d.date
  error.value = ''
}
function backToDays() {
  pickedDate.value = ''
  error.value = ''
}

function remainingClass(s) {
  if (s.full_flag) return 'tag full'
  return s.remaining <= 1 ? 'tag few' : 'tag ok'
}

async function book(slot) {
  if (busy.value) return
  if (slot.full_flag) { error.value = '该时段已约满'; return }
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    const r = await patientPortal.book({
      clinicId: slot.clinic_id,
      slotDate: String(slot.slot_date).slice(0, 10),
      slotCode: slot.slot_code
    })
    notice.value = `预约成功，预约号 ${r.booking_no}（${slot.clinic_name} ${slot.slot_name}）`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function startReschedule(a) {
  rescheduling.value = a.appointment_id
  pickedDate.value = ''
  error.value = ''
  notice.value = '正在改约：请选择新的日期，再选择当天的新时段'
}

async function doReschedule(slot) {
  const id = rescheduling.value
  if (!id || busy.value) return
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    await patientPortal.reschedule(id, {
      clinicId: slot.clinic_id,
      slotDate: String(slot.slot_date).slice(0, 10),
      slotCode: slot.slot_code
    })
    notice.value = '改约成功，原时段名额已释放'
    rescheduling.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function cancel(a) {
  if (busy.value) return
  if (!confirm(`确认取消预约 ${a.booking_no} 吗？名额将立即释放。`)) return
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    await patientPortal.cancel(a.appointment_id)
    notice.value = '已取消预约'
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function statusText(s) {
  return { BOOKED: '已预约', ARRIVED: '已到诊', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '爽约' }[s] || s
}
function slotClick(slot) {
  if (busy.value) return
  if (slot.full_flag) { error.value = '该时段已约满'; return }
  if (rescheduling.value) doReschedule(slot)
  else book(slot)
}
</script>

<template>
  <section>
    <h2>我的预约</h2>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <h3>当前预约（{{ activeCount }} 条有效）</h3>
    <table v-if="activeList.length">
      <thead>
        <tr>
          <th>预约号</th><th>诊所</th><th>日期时段</th><th>接诊医生</th><th>状态</th><th>提醒</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in activeList" :key="a.appointment_id">
          <td>{{ a.booking_no || a.appointment_id }}</td>
          <td>{{ a.clinic_name }}</td>
          <td>{{ fmtDate(a.starts_at) }}<br /><span class="muted">{{ a.slot_name }}</span></td>
          <td>{{ a.dentist_name || '待分配' }}</td>
          <td>{{ statusText(a.status) }}</td>
          <td>
            <span v-if="a.reminder_status === 'PENDING'" class="tag ok">{{ fmtTime(a.reminder_at) }}</span>
            <span v-else class="muted">—</span>
          </td>
          <td>
            <template v-if="['BOOKED', 'ARRIVED'].includes(a.status)">
              <button class="link" @click="startReschedule(a)">改约</button>
              <button class="link danger" @click="cancel(a)">取消</button>
            </template>
            <span v-else class="muted">—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无进行中的预约。</p>

    <button v-if="rescheduling" class="secondary" @click="rescheduling = null">取消改约操作</button>

    <h3>{{ rescheduling ? '选择改约到的新时段' : '预约就诊' }}</h3>

    <!-- 第二步：当天的时段 -->
    <template v-if="pickedDate">
      <div class="row">
        <h4>{{ pickedDate }} 当天可预约时段（{{ daySlots.length }} 个）</h4>
        <button class="secondary" @click="backToDays">← 换个日期</button>
      </div>
      <div class="slot-grid">
        <div v-for="s in daySlots" :key="s.clinic_id + s.slot_code"
             :class="['slot-card', { disabled: s.full_flag || busy }]" @click="slotClick(s)">
          <div class="slot-clinic">{{ s.clinic_name }}</div>
          <div class="slot-name">{{ s.slot_name }}（{{ s.period_code }}）</div>
          <div :class="remainingClass(s)">余 {{ s.remaining }} / {{ s.capacity }}</div>
          <div class="muted small">值班：{{ s.duty_dentists || '—' }}</div>
        </div>
      </div>
      <p v-if="!daySlots.length" class="muted">当天没有可预约时段，请返回选择其他日期。</p>
    </template>

    <!-- 第一步：日期列表 -->
    <template v-else>
      <h4>请选择就诊日期</h4>
      <div class="day-grid">
        <button v-for="d in dayOptions" :key="d.date" class="day-card" @click="pickDay(d)">
          <div class="day-date">{{ d.date }}</div>
          <div class="day-meta">{{ d.clinics.join('、') }}</div>
          <div :class="d.free > 0 ? 'tag ok' : 'tag full'">
            {{ d.free > 0 ? `剩余 ${d.free} / ${d.total} 个名额` : '当天已约满' }}
          </div>
        </button>
      </div>
      <p v-if="!dayOptions.length" class="muted">未来 7 天暂无可预约时段。</p>
    </template>
  </section>
</template>

<style scoped>
.day-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; margin: 10px 0 6px; }
.day-card {
  border: 1px solid #d7dfeb; border-radius: 10px; padding: 12px;
  background: #fff; color: #172033; /* 覆盖全局 button 白色字 */
  cursor: pointer; text-align: left; margin: 0;
}
.day-card:hover { border-color: #2563eb; background: #f0f7ff; }
.day-date { font-weight: 600; font-size: 14px; }
.day-meta { font-size: 12px; color: #6b7a90; margin: 4px 0 8px; }
.row { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.slot-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 10px; margin: 10px 0; }
.slot-card {
  border: 1px solid #d7dfeb; border-radius: 10px; padding: 10px 12px;
  background: #f8fbff; cursor: pointer; transition: .15s;
}
.slot-card:hover { border-color: #2563eb; background: #eef4ff; }
.slot-card.disabled { background: #f1f2f4; border-color: #e2e4e8; cursor: not-allowed; opacity: .7; }
.slot-clinic { font-weight: 600; color: #1f3a5f; }
.slot-name { font-size: 13px; margin: 2px 0 6px; }
.tag { display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.tag.ok { background: #dcfce7; color: #166534; }
.tag.few { background: #fef9c3; color: #854d0e; }
.tag.full { background: #fee2e2; color: #991b1b; }
.small { font-size: 12px; }
.danger { color: #b91c1c; }
button.link { border: none; background: none; padding: 0 6px; cursor: pointer; }
</style>
