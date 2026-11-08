<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({ content: { type: String, default: '' } })
// Model output is untrusted: keep raw HTML disabled and the parser's URL checks.
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })
const linkOpen = markdown.renderer.rules.link_open || ((tokens, index, options, env, self) => self.renderToken(tokens, index, options))
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return linkOpen(tokens, index, options, env, self)
}
markdown.renderer.rules.table_open = () => '<div class="markdown-table-scroll"><table>'
markdown.renderer.rules.table_close = () => '</table></div>'
const rendered = computed(() => markdown.render(props.content || ''))
</script>

<template><div class="markdown-content markdown-body" v-html="rendered" /></template>

<style scoped>
.markdown-content { min-width: 0; max-width: 100%; color: inherit; font-size: inherit; line-height: 1.85; overflow-wrap: anywhere; }
.markdown-content :deep(> :first-child) { margin-top: 0; }
.markdown-content :deep(> :last-child) { margin-bottom: 0; }
.markdown-content :deep(h1), .markdown-content :deep(h2), .markdown-content :deep(h3), .markdown-content :deep(h4), .markdown-content :deep(h5), .markdown-content :deep(h6) { margin: 1.2em 0 .6em; font-weight: 700; line-height: 1.5; }
.markdown-content :deep(h1) { font-size: 1.5em; }
.markdown-content :deep(h2) { font-size: 1.3em; }
.markdown-content :deep(h3) { font-size: 1.15em; }
.markdown-content :deep(p) { margin: .7em 0; }
.markdown-content :deep(strong) { font-weight: 700; }
.markdown-content :deep(em) { font-style: italic; }
.markdown-content :deep(ul), .markdown-content :deep(ol) { margin: .7em 0; padding-left: 1.6em; }
.markdown-content :deep(ul) { list-style: disc; }
.markdown-content :deep(ol) { list-style: decimal; }
.markdown-content :deep(li) { margin: .35em 0; padding-left: .15em; }
.markdown-content :deep(blockquote) { margin: 1em 0; padding: .3em 1em; border-left: 3px solid #48a78c; color: inherit; background: color-mix(in srgb, #48a78c 7%, transparent); border-radius: 0 8px 8px 0; }
.markdown-content :deep(a) { color: #168669; text-decoration: underline; text-underline-offset: 3px; }
.dark .markdown-content :deep(a) { color: #a5e4cd; }
.markdown-content :deep(code) { font-family: Consolas, 'SFMono-Regular', monospace; font-size: .88em; padding: .1em .35em; border-radius: 4px; background: #7f928815; }
.markdown-content :deep(pre) { max-width: 100%; margin: 1em 0; padding: 1em; overflow-x: auto; border: 1px solid #7f928830; border-radius: 10px; background: #7f928810; line-height: 1.6; }
.markdown-content :deep(pre code) { padding: 0; background: none; white-space: pre; overflow-wrap: normal; }
.markdown-content :deep(.markdown-table-scroll) { max-width: 100%; overflow-x: auto; margin: 1em 0; }
.markdown-content :deep(table) { min-width: 100%; border-collapse: collapse; font-size: .95em; }
.markdown-content :deep(th), .markdown-content :deep(td) { border: 1px solid #7f928840; padding: .5em .8em; text-align: left; }
.markdown-content :deep(th) { font-weight: 650; background: #7f928810; }
.markdown-content :deep(hr) { margin: 1.4em 0; border: 0; border-top: 1px solid #7f928840; }
.markdown-content :deep(img) { max-width: 100%; height: auto; border-radius: 8px; }
</style>
