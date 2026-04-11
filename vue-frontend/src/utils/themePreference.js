const GLOBAL_THEME_KEY = 'leafquery-theme'
const LEGACY_THEME_KEYS = ['pc-theme', 'app-theme']

const normalizeTheme = (value) => value === 'dark' ? 'dark' : 'light'

export const getStoredThemePreference = (preferredLegacyKey = null) => {
  const globalTheme = localStorage.getItem(GLOBAL_THEME_KEY)
  if (globalTheme) {
    return normalizeTheme(globalTheme)
  }

  if (preferredLegacyKey) {
    const preferredTheme = localStorage.getItem(preferredLegacyKey)
    if (preferredTheme) {
      return normalizeTheme(preferredTheme)
    }
  }

  for (const key of LEGACY_THEME_KEYS) {
    const theme = localStorage.getItem(key)
    if (theme) {
      return normalizeTheme(theme)
    }
  }

  return 'light'
}

export const persistThemePreference = (theme) => {
  const normalizedTheme = normalizeTheme(theme)
  localStorage.setItem(GLOBAL_THEME_KEY, normalizedTheme)
  LEGACY_THEME_KEYS.forEach((key) => {
    localStorage.setItem(key, normalizedTheme)
  })
  return normalizedTheme
}

export const applyThemePreference = (theme) => {
  const normalizedTheme = persistThemePreference(theme)
  document.documentElement.classList.toggle('dark', normalizedTheme === 'dark')
  return normalizedTheme
}

export const syncThemePreference = (preferredLegacyKey = null) => {
  return applyThemePreference(getStoredThemePreference(preferredLegacyKey))
}
