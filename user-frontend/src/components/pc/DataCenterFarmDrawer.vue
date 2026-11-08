<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import PCFarmManager from './PCFarmManager.vue'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['close'])
const drawer = ref(null)
const syncOpen = async () => {
  await nextTick()
  if (props.open && !drawer.value?.open) drawer.value?.showModal()
  else if (!props.open && drawer.value?.open) drawer.value.close()
}
watch(() => props.open, syncOpen)
onMounted(syncOpen)
const closeOnBackdrop = event => {
  if (event.target !== drawer.value) return
  const bounds = drawer.value.getBoundingClientRect()
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <dialog ref="drawer" class="data-center-farm-drawer" aria-labelledby="farm-drawer-title" @cancel.prevent="emit('close')" @close="emit('close')" @click="closeOnBackdrop">
      <header class="farm-drawer-header"><div><p>我的农场</p><h2 id="farm-drawer-title">农场档案管理</h2></div><button type="button" aria-label="关闭农场档案" @click="emit('close')">✕</button></header>
      <div class="farm-drawer-body"><PCFarmManager v-if="open" compact title="作物档案" subtitle="管理作物、地区与物候期，更新看板的预测范围。" /></div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.data-center-farm-drawer { position: fixed; inset: 0 0 0 auto; margin: 0; width: min(680px, 100vw); max-width: 100vw; height: 100dvh; max-height: 100dvh; padding: 0; border: 0; color: #0f172a; background: #fff; box-shadow: -12px 0 40px #0f172a12; }
.data-center-farm-drawer[open] { display: flex; flex-direction: column; animation: farm-drawer-in .25s ease-out; }
.data-center-farm-drawer::backdrop { background: #0f172a66; backdrop-filter: blur(3px); }
.farm-drawer-header { display: flex; flex-shrink: 0; align-items: center; justify-content: space-between; padding: 24px; border-bottom: 1px solid #e2e8f0; }
.farm-drawer-header p { font-size: 11px; color: #10b981; margin-bottom: 4px; }
.farm-drawer-header h2 { font-size: 18px; font-weight: 750; }
.farm-drawer-header button { width: 36px; height: 36px; border-radius: 50%; background: #f1f5f9; color: #64748b; }
.farm-drawer-body { flex: 1; min-height: 0; overflow-y: auto; padding: 20px; }
.farm-drawer-body :deep(button) { border-radius: 9999px; }
:global(.dark .data-center-farm-drawer) { background: #0b1120; color: #e2e8f0; }
:global(.dark .farm-drawer-header) { border-color: #1e293b; }
:global(.dark .farm-drawer-header button) { background: #1e293b; color: #cbd5e1; }
@keyframes farm-drawer-in { from { opacity: 0; transform: translateX(32px); } to { opacity: 1; transform: translateX(0); } }
@media (prefers-reduced-motion: reduce) { .data-center-farm-drawer[open] { animation: none; } }
</style>
