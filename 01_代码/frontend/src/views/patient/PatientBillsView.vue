<script setup>
import { computed, onMounted, ref } from 'vue'
import { patientPortal, fmtDate } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })
const list = ref([])
const error = ref('')

const totalAmount = computed(() => list.value.reduce((s, i) => s + Number(i.total_amount || 0), 0))
const totalPaid = computed(() => list.value.reduce((s, i) => s + Number(i.paid_amount || 0), 0))
const totalUnpaid = computed(() => list.value.reduce((s, i) => s + Number(i.unpaid_amount || 0), 0))

onMounted(load)

async function load() {
  error.value = ''
  try {
    list.value = await patientPortal.invoices()
  } catch (e) {
    error.value = e.message
  }
}

function statusText(s) {
  return { DRAFT: '草稿', CONFIRMED: '待付款', PAID: '已结清', VOID: '已作废' }[s] || s
}
function isUnpaid(i) { return Number(i.unpaid_amount || 0) > 0 }
</script>

<template>
  <section>
    <h2>我的账单</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="summary">
      <div class="sum-card"><span>账单总额</span><strong>¥{{ totalAmount.toFixed(2) }}</strong></div>
      <div class="sum-card"><span>已支付</span><strong class="paid">¥{{ totalPaid.toFixed(2) }}</strong></div>
      <div class="sum-card"><span>待支付</span><strong class="unpaid">¥{{ totalUnpaid.toFixed(2) }}</strong></div>
    </div>

    <table v-if="list.length">
      <thead>
        <tr><th>账单编号</th><th>诊所</th><th>开票日期</th><th>金额</th><th>已付</th><th>待付</th><th>状态</th></tr>
      </thead>
      <tbody>
        <tr v-for="i in list" :key="i.invoice_id + '-' + i.version_no">
          <td>{{ i.invoice_no }}<span class="muted"> v{{ i.version_no }}</span></td>
          <td>{{ i.clinic_name }}</td>
          <td>{{ fmtDate(i.issued_at) }}</td>
          <td>¥{{ Number(i.total_amount).toFixed(2) }}</td>
          <td>¥{{ Number(i.paid_amount).toFixed(2) }}</td>
          <td :class="{ unpaid: isUnpaid(i) }">¥{{ Number(i.unpaid_amount).toFixed(2) }}</td>
          <td>{{ statusText(i.invoice_status) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无账单。完成就诊并结算后即可在此查看。</p>

    <p class="muted small">付款请到前台扫码或由前台代收；如有疑问请联系前台核对。</p>
  </section>
</template>

<style scoped>
.summary { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.sum-card {
  border: 1px solid #e3e8f0; border-radius: 10px; padding: 12px 20px;
  background: #fbfcfe; min-width: 150px;
}
.sum-card span { display: block; color: #6b7a90; font-size: 13px; }
.sum-card strong { font-size: 20px; color: #1f3a5f; }
.paid { color: #166534; }
.unpaid { color: #b91c1c; }
.small { font-size: 12px; }
</style>
