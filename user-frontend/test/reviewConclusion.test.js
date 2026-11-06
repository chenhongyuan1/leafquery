import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { isMeaninglessReview } from '../src/utils/reviewConclusion.js'

const cases = JSON.parse(readFileSync(new URL('../../src/test/resources/report-eligibility.json', import.meta.url), 'utf8'))
for (const item of cases) {
  test(item.name, () => assert.equal(isMeaninglessReview(item.reviewResult), item.blocked))
}
