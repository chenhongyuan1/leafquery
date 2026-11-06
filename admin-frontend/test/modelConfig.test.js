import assert from 'node:assert/strict'
import test from 'node:test'
import { getChatEndpoint, getModelField, validateModelValue } from '../src/utils/modelConfig.js'

test('endpoint preview accepts the same base, complete and legacy paths as the backend', () => {
  const cases = [
    ['https://model.example.test/v1', 'https://model.example.test/v1/chat/completions'],
    ['https://model.example.test/v1/', 'https://model.example.test/v1/chat/completions'],
    ['https://model.example.test/v1/chat/completions', 'https://model.example.test/v1/chat/completions'],
    ['https://model.example.test/v1/chat/completions/', 'https://model.example.test/v1/chat/completions'],
    ['https://model.example.test/api/v3/responses', 'https://model.example.test/api/v3/chat/completions'],
    ['https://model.example.test/api/v3/responses/', 'https://model.example.test/api/v3/chat/completions'],
    ['https://model.example.test', 'https://model.example.test/v1/chat/completions'],
    ['https://model.example.test/api/v3/?tenant=demo', 'https://model.example.test/api/v3/chat/completions?tenant=demo']
  ]
  for (const [input, expected] of cases) assert.equal(getChatEndpoint(input), expected)
})

test('empty values retain the server fallback while surrounding whitespace is accepted', () => {
  assert.equal(getChatEndpoint(''), '')
  assert.equal(getChatEndpoint('  https://model.example.test/v1  '), 'https://model.example.test/v1/chat/completions')
  for (const key of ['doubao.api.url', 'doubao.api.key', 'doubao.api.model']) assert.equal(validateModelValue(key, '  '), '')
})

test('invalid addresses and unsupported protocols are rejected before saving', () => {
  for (const value of ['model.example.test/v1', 'javascript:alert(1)', 'file:///tmp/model', 'ftp://model.example.test']) {
    assert.notEqual(validateModelValue('doubao.api.url', value), '')
  }
})

test('model and key fields preserve provider IDs but reject embedded whitespace', () => {
  assert.equal(validateModelValue('doubao.api.model', 'provider/model-v2'), '')
  assert.notEqual(validateModelValue('doubao.api.model', 'model\nother'), '')
  assert.notEqual(validateModelValue('doubao.api.key', 'test key'), '')
  assert.equal(getModelField('doubao.api.key').type, 'password')
  assert.equal(getModelField('unknown.config'), null)
  assert.equal(validateModelValue('yolo.model_path', 'path with spaces.pt'), '')
})
