<script setup>
import { computed, onMounted, ref } from 'vue'
import { encounters, appointments, fmtTime, money, todayLocal } from '../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const todayAppointments = ref([])
const detail = ref(null)
const error = ref('')
const notice = ref('')
const busy = ref(false)
const editForm = ref({ chiefComplaint: '', diagnosis: '', specialNeeds: '' })
const lineForm = ref({ treatmentItemId: '', quantity: 1, notes: '' })

const isPatient = computed(() => props.account.roles.includes('PATIENT'))
const isDentist = computed(() => props.account.roles.includes('DENTIST'))
const isReception = computed(() =>
  props.account.roles.some(r => ['RECEPTION', 'MANAGER'].includes(r)))

onMounted(load)

async function load() {
  error.value = ''
  try {
    list.value = await encounters.list()
    if (isReception.value) {
      const all = await appointments.list()
      const today = todayLocal()
      todayAppointments.value = all.filter(a =>
        String(a.starts_at).slice(0, 10) === today &&
        ['BOOKED', 'CONFIRMED'].includes(a.status))
    }
  } catch (e) {
    error.value = e.message
  }
}

async function checkIn(appointmentId) {
  error.value = ''
  notice.value = ''
  try {
    await encounters.checkIn(appointmentId)
    notice.value = '签到成功，已生成就诊记录'
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function openDetail(id) {
  error.value = ''
  try {
    detail.value = await encounters.detail(id)
    editForm.value = {
      chiefComplaint: detail.value.chief_complaint || '',
      diagnosis: detail.value.diagnosis || '',
      specialNeeds: detail.value.special_needs || ''
    }
    lineForm.value = { treatmentItemId: '', quantity: 1, notes: '' }
  } catch (e) {
    error.value = e.message
  }
}

async function saveFields() {
  error.value = ''
  notice.value = ''
  try {
    await encounters.update(detail.value.encounter_id, { ...editForm.value })
    notice.value = '就诊信息已更新'
    await refreshDetail()
  } catch (e) {
    error.value = e.message
  }
}

async function changeStatus(status) {
  error.value = ''
  notice.value = ''
  try {
    await encounters.update(detail.value.encounter_id, { status })
    notice.value = status === 'IN_PROGRESS' ? '就诊已开始' : status === 'COMPLETED' ? '就诊已完成，预约同步完成' : '状态已更新'
    await Promise.all([refreshDetail(), load()])
  } catch (e) {
    error.value = e.message
  }
}

async function addLine() {
  error.value = ''
  notice.value = ''
  if (!lineForm.value.treatmentItemId) {
    error.value = '请填写治疗项目编号'
    return
  }
  try {
    await encounters.addTreatmentLine(detail.value.encounter_id, {
      treatmentItemId: Number(lineForm.value.treatmentItemId),
      quantity: Number(lineForm.value.quantity),
      notes: lineForm.value.notes || null
    })
    notice.value = '治疗项目已登记'
    lineForm.value = { treatmentItemId: '', quantity: 1, notes: '' }
    await refreshDetail()
  } catch (e) {
    error.value = e.message
  }
}

async function removeLine(lineId) {
  if (!confirm('确定删除该治疗项目吗？')) return
  error.value = ''
  try {
    await encounters.removeTreatmentLine(detail.value.encounter_id, lineId)
    notice.value = '治疗项目已删除'
    await refreshDetail()
  } catch (e) {
    error.value = e.message
  }
}

async function refreshDetail() {
  if (detail.value) {
    detail.value = await encounters.detail(detail.value.encounter_id)
  }
}

const statusText = {
  ARRIVED: '已签到', IN_PROGRESS: '就诊中', COMPLETED: '已完成', VOID: '已作废'
}
</script>

<template>
  <div>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 前台：今日待签到 -->
    <div class="card actions" v-if="isReception">
      <h2>今日待签到预约</h2>
      <table v-if="todayAppointments.length">
        <thead>
          <tr><th>预约编号</th><th>患者</th><th>牙医</th><th>开始时间</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in todayAppointments" :key="a.appointment_id">
            <td>{{ a.appointment_id }}</td>
            <td>{{ a.patient_name }}</td>
            <td>{{ a.dentist_id }}</td>
            <td>{{ fmtTime(a.starts_at) }}</td>
            <td><span class="tag">{{ a.status }}</span></td>
            <td><button class="mini" @click="checkIn(a.appointment_id)">签到</button></td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">今日没有待签到的预约。</p>
    </div>

    <!-- 就诊列表 -->
    <div class="card">
      <h2>{{ isPatient ? '我的就诊记录' : '就诊记录' }}</h2>
      <table>
        <thead>
          <tr><th>就诊编号</th><th>预约编号</th><th>患者</th><th>牙医</th><th>主诉</th><th>状态</th><th>签到时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in list" :key="e.encounter_id">
            <td>{{ e.encounter_id }}</td>
            <td>{{ e.appointment_id }}</td>
            <td>{{ e.patient_name }}（#{{ e.patient_id }}）</td>
            <td>{{ e.dentist_name }}（{{ e.dentist_id }}）</td>
            <td class="muted">{{ e.chief_complaint || '—' }}</td>
            <td><span class="tag">{{ statusText[e.status] || e.status }}</span></td>
            <td>{{ fmtTime(e.arrived_at) }}</td>
            <td><button class="mini" @click="openDetail(e.encounter_id)">详情</button></td>
          </tr>
          <tr v-if="!list.length"><td colspan="8" class="muted">暂无就诊记录</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 就诊详情 -->
    <div class="card actions" v-if="detail">
      <h2>就诊 #{{ detail.encounter_id }}（预约 #{{ detail.appointment_id }}）</h2>
      <p>
        <b>患者：</b>{{ detail.patient_name }}　<b>牙医：</b>{{ detail.dentist_name }}
        <b>状态：</b>{{ statusText[detail.status] || detail.status }}
      </p>

      <template v-if="!isPatient">
        <div class="form-grid">
          <label>主诉<input v-model="editForm.chiefComplaint" /></label>
          <label>特殊需求<input v-model="editForm.specialNeeds" /></label>
        </div>
        <label>诊断<input v-model="editForm.diagnosis" /></label>
        <button class="mini" @click="saveFields">保存就诊信息</button>

        <div class="form-grid" v-if="isDentist">
          <label>开始就诊
            <button class="mini" :disabled="detail.status !== 'ARRIVED'" @click="changeStatus('IN_PROGRESS')">开始就诊</button>
          </label>
          <label>完成就诊
            <button class="mini" :disabled="!['ARRIVED','IN_PROGRESS'].includes(detail.status)" @click="changeStatus('COMPLETED')">完成就诊</button>
          </label>
        </div>
      </template>

      <h3>治疗项目</h3>
      <table v-if="detail.treatmentLines.length">
        <thead>
          <tr><th>项目编号</th><th>名称</th><th>数量</th><th>单位</th><th>单价</th><th>小计</th><th>备注</th><th v-if="isDentist">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.treatmentLines" :key="l.treatment_line_id">
            <td>{{ l.item_code }}</td>
            <td>{{ l.name_snapshot }}</td>
            <td>{{ l.quantity }}</td>
            <td>{{ l.unit_snapshot }}</td>
            <td>{{ money(l.unit_price_snapshot) }}</td>
            <td>{{ money(l.line_total) }}</td>
            <td class="muted">{{ l.notes || '—' }}</td>
            <td v-if="isDentist">
              <button class="mini danger" @click="removeLine(l.treatment_line_id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">尚未登记治疗项目。</p>

      <template v-if="isDentist && ['ARRIVED','IN_PROGRESS'].includes(detail.status)">
        <div class="form-grid">
          <label>治疗项目编号<input v-model="lineForm.treatmentItemId" type="number" placeholder="TREATMENT_ITEM.treatment_item_id" /></label>
          <label>数量<input v-model="lineForm.quantity" type="number" min="0.1" step="0.1" /></label>
        </div>
        <label>备注<input v-model="lineForm.notes" /></label>
        <button class="mini" @click="addLine">登记治疗项目（价格按当前有效价目快照）</button>
      </template>

      <button class="link" @click="detail = null">收起详情</button>
    </div>
  </div>
</template>
