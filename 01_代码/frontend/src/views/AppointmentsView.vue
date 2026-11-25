<script setup>
import { computed, onMounted, ref } from 'vue'
import { appointments, patients, shifts, fmtTime, todayLocal } from '../api.js'
import SlotPicker from './SlotPicker.vue'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const shiftsAll = ref([])
const patientList = ref([])
const error = ref('')
const notice = ref('')
const busy = ref(false)

const isPatient = computed(() => props.account.roles.includes('PATIENT'))
const ownPatientId = computed(() => (props.account.patientId === '' || props.account.patientId == null)
  ? null : Number(props.account.patientId))

const todayStr = () => todayLocal()
const booking = ref({ patientId: '', dentistId: '', date: todayStr(), slot: null })
const reschedule = ref(null) // { id, dentistId, date, slot }

const dentists = computed(() => {
  const map = new Map()
  for (const s of shiftsAll.value) {
    if (!map.has(s.dentist_id)) map.set(s.dentist_id, s)
  }
  return [...map.entries()].map(([id, s]) => ({ id, clinicId: s.clinic_id }))
})

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [a, s] = await Promise.all([appointments.list(), appointments.shifts()])
    list.value = a
    shiftsAll.value = s
    if (!isPatient.value) {
      patientList.value = await patients.list()
    } else {
      booking.value.patientId = ownPatientId.value
    }
  } catch (e) {
    error.value = e.message
  }
}

function resetBookingSlot() { booking.value.slot = null }

async function createAppointment() {
  error.value = ''
  notice.value = ''
  if (!booking.value.slot) { error.value = '请在下方选择一个可用时段'; return }
  busy.value = true
  try {
    await appointments.create({
      patientId: Number(booking.value.patientId),
      shiftId: Number(booking.value.slot.shiftId),
      startsAt: booking.value.slot.start,
      endsAt: booking.value.slot.end
    })
    notice.value = '预约成功'
    booking.value.slot = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function cancelAppointment(id) {
  if (!confirm('确定取消这条预约吗？')) return
  error.value = ''
  notice.value = ''
  try {
    await appointments.cancel(id)
    notice.value = '预约已取消'
    await load()
  } catch (e) {
    error.value = e.message
  }
}

function openReschedule(a) {
  reschedule.value = {
    id: a.appointment_id,
    dentistId: String(a.dentist_id),
    date: String(a.starts_at).slice(0, 10),
    slot: null
  }
}

async function submitReschedule() {
  error.value = ''
  notice.value = ''
  if (!reschedule.value.slot) { error.value = '请选择一个新时段'; return }
  try {
    await appointments.reschedule(reschedule.value.id, {
      shiftId: Number(reschedule.value.slot.shiftId),
      startsAt: reschedule.value.slot.start,
      endsAt: reschedule.value.slot.end
    })
    notice.value = '改约成功'
    reschedule.value = null
    await load()
  } catch (e) {
    error.value = e.message
  }
}

const statusText = {
  BOOKED: '已预约', CONFIRMED: '已确认', ARRIVED: '已签到',
  CANCELLED: '已取消', NO_SHOW: '未赴约', COMPLETED: '已完成'
}
</script>

<template>
  <div>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 新建预约 -->
    <div class="card actions">
      <h2>{{ isPatient ? '预约就诊' : '新建预约' }}</h2>
      <div class="form-grid">
        <label v-if="!isPatient">患者
          <select v-model="booking.patientId">
            <option value="" disabled>请选择患者</option>
            <option v-for="p in patientList" :key="p.patient_id" :value="p.patient_id">
              #{{ p.patient_id }} - {{ p.name }}
            </option>
          </select>
        </label>
        <label>牙医
          <select v-model="booking.dentistId" @change="resetBookingSlot">
            <option value="" disabled>请选择牙医</option>
            <option v-for="d in dentists" :key="d.id" :value="d.id">牙医 #{{ d.id }}</option>
          </select>
        </label>
        <label>日期
          <input type="date" v-model="booking.date" @change="resetBookingSlot" />
        </label>
      </div>

      <template v-if="booking.dentistId && booking.date">
        <p class="muted">下方为该牙医当天的可预约时间块（每格 30 分钟），点击可选：</p>
        <SlotPicker :dentist-id="booking.dentistId" :date="booking.date" v-model="booking.slot" />
      </template>

      <button @click="createAppointment" :disabled="busy || !booking.slot">
        {{ busy ? '提交中…' : '提交预约' }}
      </button>
    </div>

    <!-- 改约弹层 -->
    <div class="card actions" v-if="reschedule">
      <h2>改约（预约 #{{ reschedule.id }}）</h2>
      <div class="form-grid">
        <label>牙医
          <select v-model="reschedule.dentistId">
            <option v-for="d in dentists" :key="d.id" :value="String(d.id)">牙医 #{{ d.id }}</option>
          </select>
        </label>
        <label>日期<input type="date" v-model="reschedule.date" /></label>
      </div>
      <template v-if="reschedule.dentistId && reschedule.date">
        <SlotPicker :dentist-id="reschedule.dentistId" :date="reschedule.date" v-model="reschedule.slot" />
      </template>
      <button @click="submitReschedule" :disabled="!reschedule.slot">确认改约</button>
      <button class="link" @click="reschedule = null">取消</button>
    </div>

    <!-- 预约列表 -->
    <div class="card">
      <h2>预约列表</h2>
      <table>
        <thead>
          <tr><th>编号</th><th>患者</th><th>牙医</th><th>开始时间</th><th>结束时间</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in list" :key="a.appointment_id">
            <td>{{ a.appointment_id }}</td>
            <td>{{ a.patient_name }}（#{{ a.patient_id }}）</td>
            <td>{{ a.dentist_id }}</td>
            <td>{{ fmtTime(a.starts_at) }}</td>
            <td>{{ fmtTime(a.ends_at) }}</td>
            <td><span class="tag">{{ statusText[a.status] || a.status }}</span></td>
            <td>
              <button class="mini" v-if="!['CANCELLED', 'COMPLETED'].includes(a.status)"
                      @click="openReschedule(a)">改约</button>
              <button class="mini danger" v-if="!['CANCELLED', 'COMPLETED'].includes(a.status)"
                      @click="cancelAppointment(a.appointment_id)">取消</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="7" class="muted">暂无预约记录</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
