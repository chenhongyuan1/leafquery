<script setup>
defineProps({
  isDark: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})
</script>

<template>
  <div class="leafquery-brand" :class="{ 'leafquery-brand--dark': isDark, 'leafquery-brand--compact': compact }">
    <span class="brand-icon"><img src="/vite.svg" alt="" width="36" height="36" /></span>
    <div class="brand-copy" :aria-hidden="compact">
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
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  transition: gap 200ms ease;
  font-family: 'Segoe UI', 'Microsoft YaHei', 'PingFang SC', sans-serif;
}
.leafquery-brand--dark {
  --brand-text: #e9f4ef;
  --brand-muted: #a4bdb8;
  --brand-accent: #65dec0;
  --brand-line: rgba(141, 207, 187, 0.13);
}
.brand-icon { width: 46px; height: 46px; flex-shrink: 0; display: grid; place-items: center; }
.brand-copy { max-width: 200px; overflow: hidden; opacity: 1; transition: max-width 200ms ease, opacity 160ms ease; }
.leafquery-brand--compact { gap: 0; }
.leafquery-brand--compact .brand-copy { max-width: 0; opacity: 0; }
.brand-name { display: flex; align-items: center; gap: 10px; color: var(--brand-text); font-size: 24px; font-weight: 800; letter-spacing: -0.6px; line-height: 1.2; white-space: nowrap; }
.brand-pc { color: var(--brand-accent); border: 1px solid var(--brand-line); border-radius: 4px; padding: 3px 5px; font: 11px/1 ui-monospace, monospace; letter-spacing: 0.5px; }
.brand-caption { margin: 6px 0 0; color: var(--brand-muted); font-size: 14px; line-height: 1.5; letter-spacing: 1px; white-space: nowrap; }
@media (prefers-reduced-motion: reduce) {
  .leafquery-brand, .brand-copy { transition: none; }
}
</style>
