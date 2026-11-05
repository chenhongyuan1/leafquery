const interactiveTags = new Set(['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'SUMMARY'])
const interactiveRoles = new Set(['button', 'switch', 'tab', 'textbox', 'combobox', 'menuitem', 'slider'])

// A page shortcut must leave native keyboard activation and text composition intact.
export function shouldLaunchIntro(event) {
  if (event.key !== 'Enter' || event.defaultPrevented || event.repeat || event.isComposing ||
      event.ctrlKey || event.altKey || event.metaKey || event.shiftKey) return false

  const path = typeof event.composedPath === 'function' ? event.composedPath() : []
  if (!path.length) {
    for (let node = event.target; node; node = node.parentElement) path.push(node)
  }
  return !path.some(node => interactiveTags.has(node?.tagName) || node?.isContentEditable ||
    interactiveRoles.has(node?.getAttribute?.('role')))
}
