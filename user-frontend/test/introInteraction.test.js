import test from 'node:test'
import assert from 'node:assert/strict'
import { shouldLaunchIntro } from '../src/utils/introInteraction.js'

test('Enter on the introduction background can launch the workspace', () => {
  assert.equal(shouldLaunchIntro({ key: 'Enter', target: { tagName: 'DIV' } }), true)
  assert.equal(shouldLaunchIntro({ key: 'Escape' }), false)
})

test('native controls and nested icons retain their own Enter behavior', () => {
  for (const tagName of ['BUTTON', 'A', 'INPUT', 'SELECT', 'TEXTAREA', 'SUMMARY']) {
    assert.equal(shouldLaunchIntro({ key: 'Enter', target: { tagName } }), false)
  }
  assert.equal(shouldLaunchIntro({ key: 'Enter', target: {
    tagName: 'path', parentElement: { tagName: 'svg', parentElement: { tagName: 'BUTTON' } }
  } }), false)
})

test('shadow paths, editable content and accessible switches do not launch', () => {
  assert.equal(shouldLaunchIntro({ key: 'Enter', composedPath: () => [{ tagName: 'BUTTON' }, {}] }), false)
  assert.equal(shouldLaunchIntro({ key: 'Enter', target: { isContentEditable: true } }), false)
  assert.equal(shouldLaunchIntro({ key: 'Enter', target: { getAttribute: () => 'switch' } }), false)
})

test('composition, held keys, modifiers and handled events are preserved', () => {
  for (const flag of ['isComposing', 'repeat', 'ctrlKey', 'altKey', 'metaKey', 'shiftKey', 'defaultPrevented']) {
    assert.equal(shouldLaunchIntro({ key: 'Enter', [flag]: true }), false)
  }
})
