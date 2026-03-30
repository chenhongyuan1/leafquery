<script setup>
defineProps({
  isDark: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle'])

const handleToggle = (event) => {
  const rect = event.currentTarget.getBoundingClientRect()
  emit('toggle', {
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2
  })
}
</script>

<template>
  <button 
    class="theme-toggle focus:outline-none focus:ring-2 focus:ring-green-400 focus:ring-offset-2 dark:focus:ring-offset-slate-900 rounded-[100em] transition-shadow shadow-sm" 
    :class="{ 'dark': isDark }" 
    @click="handleToggle" 
    :disabled="disabled"
    role="switch" 
    :aria-checked="isDark.toString()" 
    :aria-label="isDark ? '切换到亮色主题' : '切换到暗色主题'"
  >
    <div class="theme-toggle__container">
      <div class="theme-toggle__clouds"></div>
      <div class="theme-toggle__stars">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 144 55" fill="none">
          <path fill-rule="evenodd" clip-rule="evenodd" d="M135.831 3.00688C135.055 3.85027 134.111 4.29946 133 4.35447C134.111 4.40947 135.055 4.85867 135.831 5.71123C136.607 6.55462 136.996 7.56303 136.996 8.72727C136.996 7.95722 137.172 7.25134 137.525 6.59129C137.886 5.93124 138.372 5.39954 138.98 5.00535C139.598 4.60199 140.268 4.39114 141 4.35447C139.88 4.2903 138.936 3.85027 138.16 3.00688C137.384 2.16348 136.996 1.16425 136.996 0C136.996 1.16425 136.607 2.16348 135.831 3.00688ZM31 23.3545C32.1114 23.2995 33.0551 22.8503 33.8313 22.0069C34.6075 21.1635 34.9956 20.1642 34.9956 19C34.9956 20.1642 35.3837 21.1635 36.1599 22.0069C36.9361 22.8503 37.8798 23.2903 39 23.3545C38.2679 23.3911 37.5976 23.602 36.9802 24.0053C36.3716 24.3995 35.8864 24.9312 35.5248 25.5913C35.172 26.2513 34.9956 26.9572 34.9956 27.7273C34.9956 26.563 34.6075 25.5546 33.8313 24.7112C33.0551 23.8587 32.1114 23.4095 31 23.3545ZM0 36.3545C1.11136 36.2995 2.05513 35.8503 2.83131 35.0069C3.6075 34.1635 3.99559 33.1642 3.99559 32C3.99559 33.1642 4.38368 34.1635 5.15987 35.0069C5.93605 35.8503 6.87982 36.2903 8 36.3545C7.26792 36.3911 6.59757 36.602 5.98015 37.0053C5.37155 37.3995 4.88644 37.9312 4.52481 38.5913C4.172 39.2513 3.99559 39.9572 3.99559 40.7273C3.99559 39.563 3.6075 38.5546 2.83131 37.7112C2.05513 36.8587 1.11136 36.4095 0 36.3545ZM56.8313 24.0069C56.0551 24.8503 55.1114 25.2995 54 25.3545C55.1114 25.4095 56.0551 25.8587 56.8313 26.7112C57.6075 27.5546 57.9956 28.563 57.9956 29.7273C57.9956 28.9572 58.172 28.2513 58.5248 27.5913C58.8864 26.9312 59.3716 26.3995 59.9802 26.0053C60.5976 25.602 61.2679 25.3911 62 25.3545C60.8798 25.2903 59.9361 24.8503 59.1599 24.0069C58.3837 23.1635 57.9956 22.1642 57.9956 21C57.9956 22.1642 57.6075 23.1635 56.8313 24.0069ZM81 25.3545C82.1114 25.2995 83.0551 24.8503 83.8313 24.0069C84.6075 23.1635 84.9956 22.1642 84.9956 21C84.9956 22.1642 85.3837 23.1635 86.1599 24.0069C86.9361 24.8503 87.8798 25.2903 89 25.3545C88.2679 25.3911 87.5976 25.602 86.9802 26.0053C86.3716 26.3995 85.8864 26.9312 85.5248 27.5913C85.172 28.2513 84.9956 28.9572 84.9956 29.7273C84.9956 28.563 84.6075 27.5546 83.8313 26.7112C83.0551 25.8587 82.1114 25.4095 81 25.3545ZM136 36.3545C137.111 36.2995 138.055 35.8503 138.831 35.0069C139.607 34.1635 139.996 33.1642 139.996 32C139.996 33.1642 140.384 34.1635 141.16 35.0069C141.936 35.8503 142.88 36.2903 144 36.3545C143.268 36.3911 142.598 36.602 141.98 37.0053C141.372 37.3995 140.886 37.9312 140.525 38.5913C140.172 39.2513 139.996 39.9572 139.996 40.7273C139.996 39.563 139.607 38.5546 138.831 37.7112C138.055 36.8587 137.111 36.4095 136 36.3545ZM101.831 49.0069C101.055 49.8503 100.111 50.2995 99 50.3545C100.111 50.4095 101.055 50.8587 101.831 51.7112C102.607 52.5546 102.996 53.563 102.996 54.7273C102.996 53.9572 103.172 53.2513 103.525 52.5913C103.886 51.9312 104.372 51.3995 104.98 51.0053C105.598 50.602 106.268 50.3911 107 50.3545C105.88 50.2903 104.936 49.8503 104.16 49.0069C103.384 48.1635 102.996 47.1642 102.996 46C102.996 47.1642 102.607 48.1635 101.831 49.0069Z" fill="currentColor"></path>
        </svg>
      </div>
      <div class="theme-toggle__sun">
        <div class="theme-toggle__moon-mask">
          <div class="theme-toggle__crater"></div>
          <div class="theme-toggle__crater"></div>
          <div class="theme-toggle__crater"></div>
        </div>
      </div>
    </div>
  </button>
</template>

<style scoped>
.theme-toggle {
  --base-scale: 11px;
  --toggle-width: 5.625em;
  --toggle-height: 2.5em;
  --radius-pill: 100em;
  --sun-diameter: 2.125em;
  --sun-offset: calc((var(--toggle-height) - var(--sun-diameter)) / 2);

  --bg-toggle-light: #3d7eae;
  --bg-toggle-dark: #1d1f2c;
  --color-sun: #ecca2f;
  --color-moon: #c4c9d1;
  --color-crater: #959db1;
  --color-star: #fff;
  --color-cloud-front: #f3fdff;
  --color-cloud-back: #aacadf;

  --shadow-primary: rgba(0, 0, 0, 0.25);
  --shadow-highlight: rgba(255, 255, 255, 0.94);
  --halo-color: rgba(255, 255, 255, 0.1);
  --transition-standard: 0.3s ease;
}

.theme-toggle {
  font-size: var(--base-scale);
  background: none;
  border: none;
  display: inline-flex;
  outline: none;
}

.theme-toggle:disabled {
  cursor: default;
}

.theme-toggle__container {
  width: var(--toggle-width);
  height: var(--toggle-height);
  background-color: var(--bg-toggle-light);
  border-radius: var(--radius-pill);
  cursor: pointer;

  position: relative;

  transition: background-color var(--transition-standard);

  overflow: hidden;

  box-shadow: 0.06em 0.06em 0.125em var(--shadow-highlight);
}

.theme-toggle.dark .theme-toggle__container {
  background-color: var(--bg-toggle-dark);
}

.theme-toggle__container::after {
  content: "";
  position: absolute;
  inset: 0;
  box-shadow: 0em 0.05em 0.187em rgba(0, 0, 0, 0.5) inset;
  border-radius: var(--radius-pill);
  pointer-events: none;
}

.theme-toggle__sun {
  width: var(--sun-diameter);
  height: var(--sun-diameter);
  background-color: var(--color-sun);
  border-radius: 50%;
  view-transition-name: theme-toggle-knob;

  position: absolute;
  top: var(--sun-offset);
  transform: translateX(var(--sun-offset));

  box-shadow: 0.05em 0.125em 0.125em var(--shadow-primary),
    0em 0.05em 0.125em var(--shadow-primary),
    0.05em 0.05em 0.05em 0em rgba(254, 255, 239, 0.61) inset,
    0em -0.05em 0.05em 0em #a1872a inset, 0 0 0 0.625em var(--halo-color),
    0 0 0 1.25em var(--halo-color), 0 0 0 1.875em var(--halo-color);

  transition: transform var(--transition-standard);

  overflow: hidden;
}

.theme-toggle.dark .theme-toggle__container .theme-toggle__sun {
  transform: translateX(
    calc(var(--toggle-width) - var(--sun-diameter) - var(--sun-offset))
  );
}

.theme-toggle__moon-mask {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: var(--color-moon);
  border-radius: inherit;
  transform: translateX(100%);
  transition: transform var(--transition-standard);

  box-shadow: 0.062em 0.062em 0.062em 0em rgba(254, 255, 239, 0.61) inset,
    0em -0.062em 0.062em 0em #969696 inset;
}

.theme-toggle.dark .theme-toggle__container .theme-toggle__moon-mask {
  transform: translateX(0);
}

.theme-toggle__crater {
  position: absolute;
  background-color: var(--color-crater);
  border-radius: 50%;
  box-shadow: 0em 0.03em 0.06em var(--shadow-primary) inset;
}

.theme-toggle__crater:nth-of-type(1) {
  top: 0.75em;
  left: 0.3em;
  width: 0.75em;
  height: 0.75em;
}

.theme-toggle__crater:nth-of-type(2) {
  top: 0.9em;
  left: 1.375em;
  width: 0.375em;
  height: 0.375em;
}

.theme-toggle__crater:nth-of-type(3) {
  top: 0.3em;
  left: 0.8em;
  width: 0.25em;
  height: 0.25em;
}

.theme-toggle__stars {
  position: absolute;
  left: 0.3em;
  transform: translateY(-2em);
  transition: transform var(--transition-standard);
  width: 2.75em;
  color: var(--color-star);
}

.theme-toggle.dark .theme-toggle__container .theme-toggle__stars {
  transform: translateY(0.5em);
}

.theme-toggle__clouds {
  position: absolute;
  left: 0.3em;
  top: 2em;
  transform: translateY(0);
  width: 1.25em;
  height: 1.25em;
  background-color: var(--color-cloud-front);
  border-radius: 50%;

  box-shadow: 0.937em 0.312em var(--color-cloud-front),
    1.437em 0.375em var(--color-cloud-front), 2.187em 0 var(--color-cloud-front),
    2.937em 0.312em var(--color-cloud-front),
    3.625em -0.062em var(--color-cloud-front),
    4.5em -0.312em var(--color-cloud-front),
    4.625em -1.75em 0 0.437em var(--color-cloud-front),
    -0.312em -0.312em var(--color-cloud-back),
    0.5em -0.125em var(--color-cloud-back),
    1.25em -0.062em var(--color-cloud-back),
    2em -0.312em var(--color-cloud-back), 2.625em 0em var(--color-cloud-back),
    3.375em -0.437em var(--color-cloud-back),
    4em -0.625em var(--color-cloud-back),
    4.125em -2.125em 0 0.437em var(--color-cloud-back);

  transition: transform var(--transition-standard);
}

.theme-toggle.dark .theme-toggle__container .theme-toggle__clouds {
  transform: translateY(3em);
}

.theme-toggle__container:hover .theme-toggle__sun {
  transform: translateX(calc(var(--sun-offset) + 0.187em));
}

.theme-toggle.dark .theme-toggle__container:hover .theme-toggle__sun {
  transform: translateX(
    calc(
      var(--toggle-width) - var(--sun-diameter) - var(--sun-offset) - 0.187em
    )
  );
}
</style>
