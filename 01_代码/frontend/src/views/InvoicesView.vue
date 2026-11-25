<script setup>
import { computed, onMounted, ref } from 'vue'
import { invoices, encounters, fmtTime, money } from '../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const encounterList = ref([])
const detail = ref(null)
const error = ref('')
const notice = ref('')
const busy = ref(false)
const payForm = ref({ amount: '', method: 'CASH' })

const isPatient = computed(() => props.account.roles.includes('PATIENT'))
const isManager = computed(() =>
  props.account.roles.some(r => ['MANAGER'].includes(r)))
const isStaff = computed(() =>
  props.account.roles.some(r => ['RECEPTION', 'MANAGER'].includes(r)))

/** 可生成账单的就诊：未作废、至少一条治疗项目、尚未生成账单 */
const billableEncounters = computed(() => {
  const invoiced = new Set(list.value.map(i => i.encounter_id))
  return encounterList.value.filter(e =>
    !invoiced.has(e.encounter_id) && e.status !== 'VOID')
})

onMounted(load)

async function load() {
  error.value = ''
  try {
    list.value = await invoices.list()
    if (isStaff.value) {
      encounterList.value = await encounters.list()
    }
  } catch (e) {
    error.value = e.message
  }
}

async function openDetail(id) {
  error.value = ''
  try {
    detail.value = await invoices.detail(id)
    const latest = detail.value.versions[detail.value.versions.length - 1]
    const paid = detail.value.payments.reduce((s, p) => s + Number(p.amount), 0)
    payForm.value = { amount: (Number(latest.total_amount) - paid).toFixed(2), method: 'CASH' }
  } catch (e) {
    error.value = e.message
  }
}

async function generate(encounterId) {
  error.value = ''
  notice.value = ''
  try {
    await invoices.generate(encounterId)
    notice.value = '账单已生成（草稿）'
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function confirm() {
  error.value = ''
  notice.value = ''
  try {
    await invoices.confirm(detail.value.invoice_id)
    notice.value = '账单已确认'
    await Promise.all([refreshDetail(), load()])
  } catch (e) {
    error.value = e.message
  }
}

async function pay() {
  error.value = ''
  notice.value = ''
  const amount = Number(payForm.value.amount)
  if (!(amount > 0)) {
    error.value = '收款金额必须大于 0'
    return
  }
  try {
    await invoices.pay(detail.value.invoice_id, { amount, method: payForm.value.method })
    notice.value = '收款成功'
    await Promise.all([refreshDetail(), load()])
  } catch (e) {
    error.value = e.message
  }
}

async function voidInvoice() {
  if (!confirm('确定作废该账单吗？')) return
  error.value = ''
  notice.value = ''
  try {
    await invoices.void(detail.value.invoice_id)
    notice.value = '账单已作废'
    await Promise.all([refreshDetail(), load()])
  } catch (e) {
    error.value = e.message
  }
}

async function refreshDetail() {
  if (detail.value) {
    detail.value = await invoices.detail(detail.value.invoice_id)
  }
}

const statusText = { DRAFT: '草稿', CONFIRMED: '已确认', VOID: '已作废', PAID: '已付清' }
const methodText = { CASH: '现金', BANK_CARD: '银行卡', MOBILE_PAYMENT: '移动支付' }
</script>

<template>
  <div>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 可生成账单的就诊 -->
    <div class="card actions" v-if="isStaff">
      <h2>待生成账单的就诊</h2>
      <table v-if="billableEncounters.length">
        <thead>
          <tr><th>就诊编号</th><th>预约编号</th><th>患者</th><th>牙医</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in billableEncounters" :key="e.encounter_id">
            <td>{{ e.encounter_id }}</td>
            <td>{{ e.appointment_id }}</td>
            <td>{{ e.patient_name }}（#{{ e.patient_id }}）</td>
            <td>{{ e.dentist_name }}</td>
            <td><span class="tag">{{ e.status }}</span></td>
            <td><button class="mini" @click="generate(e.encounter_id)">生成账单</button></td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">当前没有可生成账单的就诊（需已登记治疗项目且未生成账单）。</p>
    </div>

    <!-- 账单列表 -->
    <div class="card">
      <h2>{{ isPatient ? '我的账单' : '账单列表' }}</h2>
      <table>
        <thead>
          <tr><th>账单号</th><th>患者</th><th>牙医</th><th>应收金额</th><th>已收金额</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="i in list" :key="i.invoice_id">
            <td>{{ i.invoice_no }}</td>
            <td>{{ i.patient_name }}（#{{ i.patient_id }}）</td>
            <td>{{ i.dentist_name }}</td>
            <td>{{ money(i.total_amount) }}</td>
            <td>{{ money(i.paid_amount) }}</td>
            <td><span class="tag">{{ statusText[i.status] || i.status }}</span></td>
            <td><button class="mini" @click="openDetail(i.invoice_id)">详情</button></td>
          </tr>
          <tr v-if="!list.length"><td colspan="7" class="muted">暂无账单</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 账单详情 -->
    <div class="card actions" v-if="detail">
      <h2>账单 {{ detail.invoice_no }}</h2>
      <p>
        <b>状态：</b>{{ statusText[detail.status] || detail.status }}
        <b>就诊：</b>#{{ detail.encounter_id }}
        <b>患者：</b>{{ detail.patient_name }}
        <b>牙医：</b>{{ detail.dentist_name }}
      </p>

      <h3>账单明细（按版本快照）</h3>
      <table v-if="detail.lines.length">
        <thead>
          <tr><th>版本</th><th>项目</th><th>单位</th><th>数量</th><th>单价</th><th>小计</th></tr>
        </thead>
        <tbody>
          <tr v-for="(l, idx) in detail.lines" :key="idx">
            <td>v{{ l.version_no }}</td>
            <td>{{ l.description_snapshot }}</td>
            <td>{{ l.unit_snapshot }}</td>
            <td>{{ l.quantity_snapshot }}</td>
            <td>{{ money(l.unit_price_snapshot) }}</td>
            <td>{{ money(l.line_total) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无明细。</p>

      <h3>收款记录</h3>
      <table v-if="detail.payments.length">
        <thead>
          <tr><th>收据号</th><th>金额</th><th>方式</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in detail.payments" :key="p.payment_id">
            <td>{{ p.receipt_no }}</td>
            <td>{{ money(p.amount) }}</td>
            <td>{{ methodText[p.method] || p.method }}</td>
            <td>{{ fmtTime(p.paid_at) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无收款记录。</p>

      <template v-if="isStaff">
        <div class="form-grid" v-if="detail.status === 'DRAFT'">
          <label>确认账单
            <button class="mini" @click="confirm">确认账单</button>
          </label>
          <label v-if="isManager">作废账单
            <button class="mini danger" @click="voidInvoice">作废账单</button>
          </label>
        </div>
        <div class="form-grid" v-if="detail.status === 'CONFIRMED'">
          <label>收款方式
            <select v-model="payForm.method">
              <option value="CASH">现金</option>
              <option value="BANK_CARD">银行卡</option>
              <option value="MOBILE_PAYMENT">移动支付</option>
            </select>
          </label>
          <label>收款金额<input v-model="payForm.amount" type="number" min="0.01" step="0.01" /></label>
        </div>
        <button class="mini" v-if="detail.status === 'CONFIRMED'" @click="pay">登记收款</button>
      </template>

      <button class="link" @click="detail = null">收起详情</button>
    </div>
  </div>
</template>
