<script setup>
defineProps({ isDark: { type: Boolean, default: false } })
</script>

<template>
  <div class="leafquery-brand" :class="{ 'leafquery-brand--dark': isDark }">
    <span class="brand-icon"><img src="/vite.svg" alt="" width="36" height="36" /></span>
    <div class="brand-copy">
      <span class="brand-name">LeafQuery<span class="brand-pc">PC</span></span>
      <p class="brand-caption">农作物健康助手</p>
    </div>
  </div>
</template>

<style scoped>
.leafquery-brand {
  --brand-text: #173b3a;
  --brand-muted: #526e6c;
  --brand-accent: #008c70;
  --brand-line: rgba(28, 100, 84, 0.13);
  --brand-panel: rgba(255, 255, 255, 0.65);
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  font-family: 'Segoe UI', 'Microsoft YaHei', 'PingFang SC', sans-serif;
}
.leafquery-brand--dark {
  --brand-text: #e9f4ef;
  --brand-muted: #a4bdb8;
  --brand-accent: #65dec0;
  --brand-line: rgba(141, 207, 187, 0.13);
  --brand-panel: rgba(17, 35, 43, 0.68);
}
.brand-icon { width: 46px; height: 46px; flex-shrink: 0; display: grid; place-items: center; border: 1px solid var(--brand-line); border-radius: 13px; background: var(--brand-panel); }
.brand-name { display: flex; align-items: center; gap: 10px; color: var(--brand-text); font-size: 24px; font-weight: 800; letter-spacing: -0.6px; line-height: 1.2; white-space: nowrap; }
.brand-pc { color: var(--brand-accent); border: 1px solid var(--brand-line); border-radius: 4px; padding: 3px 5px; font: 11px/1 ui-monospace, monospace; letter-spacing: 0.5px; }
.brand-caption { margin: 6px 0 0; color: var(--brand-muted); font-size: 14px; line-height: 1.5; letter-spacing: 1px; white-space: nowrap; }
</style>
