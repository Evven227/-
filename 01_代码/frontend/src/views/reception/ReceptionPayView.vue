<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import QRCode from 'qrcode'
import { invoices, receptionPortal, fmtTime } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const error = ref('')
const notice = ref('')
const selected = ref(null)      // 选中账单
const versionId = ref(null)
const qr = ref(null)            // { img, payload, amount, expiresAt, paymentId }
let timer = null

const unpaidList = computed(() =>
  list.value.filter(i => Number(i.total_amount || 0) - Number(i.paid_amount || 0) > 0))

onMounted(load)
onUnmounted(() => { if (timer) clearInterval(timer) })

async function load() {
  error.value = ''
  try {
    list.value = await invoices.list()
  } catch (e) {
    error.value = e.message
  }
}

async function pick(inv) {
  error.value = ''; notice.value = ''
  qr.value = null
  selected.value = inv
  try {
    const d = await invoices.detail(inv.invoice_id)
    const versions = d.versions || []
    versionId.value = versions.length ? versions[versions.length - 1].invoice_version_id : null
    if (!versionId.value) error.value = '该账单没有有效版本'
  } catch (e) {
    error.value = e.message
  }
}

const remain = computed(() =>
  selected.value
    ? Number(selected.value.total_amount || 0) - Number(selected.value.paid_amount || 0)
    : 0)

async function generateQr() {
  if (!versionId.value) return
  error.value = ''; notice.value = ''
  try {
    const data = await receptionPortal.createQr({ invoiceVersionId: versionId.value, amount: remain.value })
    const img = await QRCode.toDataURL(data.qrPayload, { width: 220, margin: 1 })
    qr.value = {
      img, paymentId: data.paymentId, amount: data.amount,
      expiresAt: data.expiresAt, payload: data.qrPayload
    }
    startPolling(data.paymentId)
  } catch (e) {
    error.value = e.message
  }
}

function startPolling(paymentId) {
  if (timer) clearInterval(timer)
  timer = setInterval(async () => {
    try {
      const s = await receptionPortal.qrStatus(paymentId)
      if (s.status === 'SUCCESS') {
        clearInterval(timer); timer = null
        notice.value = `收款成功：¥${Number(s.amount).toFixed(2)}`
        qr.value = null
        selected.value = null
        await load()
      } else if (s.status === 'FAILED' || s.expired) {
        clearInterval(timer); timer = null
        error.value = '二维码已过期，请重新生成'
        qr.value = null
      }
    } catch (e) {
      clearInterval(timer); timer = null
    }
  }, 3000)
}

/** 演示用：模拟患者扫码支付成功（真实环境由支付渠道回调触发）。 */
async function mockPaid() {
  error.value = ''
  notice.value = '演示环境请到「结算收款」页点击确认收款，或使用本页二维码在支付渠道完成支付。'
}

function statusText(s) {
  return { DRAFT: '草稿', CONFIRMED: '已确认', PAID: '已支付', VOID: '已作废' }[s] || s
}
</script>

<template>
  <section>
    <h2>收款与二维码支付</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <div class="cols">
      <div>
        <h3>账单列表（{{ list.length }} 张，待收 {{ unpaidList.length }} 张）</h3>
        <table v-if="list.length">
          <thead>
            <tr><th>账单号</th><th>患者</th><th>金额</th><th>已付</th><th>待收</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="i in list" :key="i.invoice_id" :class="{ active: selected && selected.invoice_id === i.invoice_id }">
              <td>{{ i.invoice_no }}</td>
              <td>{{ i.patient_name }}</td>
              <td>¥{{ Number(i.total_amount || 0).toFixed(2) }}</td>
              <td>¥{{ Number(i.paid_amount || 0).toFixed(2) }}</td>
              <td :class="{ unpaid: Number(i.total_amount) - Number(i.paid_amount) > 0 }">
                ¥{{ (Number(i.total_amount || 0) - Number(i.paid_amount || 0)).toFixed(2) }}
              </td>
              <td>{{ statusText(i.status) }}</td>
              <td><button class="mini" @click="pick(i)">收款</button></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无账单。</p>
      </div>

      <div v-if="selected" class="qr-panel">
        <h3>收款二维码</h3>
        <p class="muted">
          账单 {{ selected.invoice_no }} · 患者 {{ selected.patient_name }}<br />
          应收总额 ¥{{ Number(selected.total_amount).toFixed(2) }} · 已付 ¥{{ Number(selected.paid_amount).toFixed(2) }}
        </p>
        <p class="big">待收 <strong>¥{{ remain.toFixed(2) }}</strong></p>

        <div v-if="qr" class="qr-box">
          <img :src="qr.img" alt="收款二维码" />
          <p class="muted small">有效期至 {{ fmtTime(qr.expiresAt) }}</p>
          <p class="muted small">等待扫码支付…（每 3 秒自动查询支付结果）</p>
          <button class="link" @click="mockPaid">如何模拟支付？</button>
        </div>
        <button v-else-if="remain > 0" @click="generateQr">生成收款二维码</button>
        <p v-else class="muted">该账单已结清。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cols { display: grid; grid-template-columns: 1fr 300px; gap: 18px; align-items: start; }
@media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
.qr-panel { border: 1px solid #e3e8f0; border-radius: 10px; padding: 12px 16px; background: #fbfcfe; }
.qr-panel h3 { margin-top: 0; }
.big { font-size: 15px; }
.big strong { font-size: 22px; color: #b91c1c; }
.qr-box { text-align: center; }
.qr-box img { width: 220px; height: 220px; border: 1px solid #e6eaf0; border-radius: 8px; background: #fff; }
.unpaid { color: #b91c1c; }
tr.active { background: #eff6ff; }
.small { font-size: 12px; }
button.mini { padding: 3px 10px; font-size: 12px; }
button.link { border: none; background: none; color: #2563eb; padding: 0; cursor: pointer; }
h3 { margin: 0 0 8px; }
</style>
