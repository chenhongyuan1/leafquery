const fs = require('fs');
const path = require('path');

const dir = 'd:/LeafQuery/leafquery/vue-frontend/src/views/pc';
const files = ['PCWorkspace.vue', 'PCDataCenter.vue', 'PCKnowledge.vue', 'PCSettings.vue'];

const replacements = [
  { match: /(?<!dark:)\bbg-white\b/g, replace: 'bg-white dark:bg-slate-900' },
  { match: /(?<!dark:)\bbg-slate-50\/50\b/g, replace: 'bg-slate-50/50 dark:bg-slate-900/50' },
  { match: /(?<!dark:)\bbg-slate-50\b(?!\/)/g, replace: 'bg-slate-50 dark:bg-slate-800' },
  { match: /(?<!dark:)\bbg-slate-100\b/g, replace: 'bg-slate-100 dark:bg-slate-800' },
  { match: /(?<!dark:)\bbg-slate-200\b/g, replace: 'bg-slate-200 dark:bg-slate-700' },
  
  { match: /(?<!dark:)\btext-slate-800\b/g, replace: 'text-slate-800 dark:text-slate-100' },
  { match: /(?<!dark:)\btext-slate-700\b/g, replace: 'text-slate-700 dark:text-slate-200' },
  { match: /(?<!dark:)\btext-slate-600\b/g, replace: 'text-slate-600 dark:text-slate-300' },
  { match: /(?<!dark:)\btext-slate-500\b/g, replace: 'text-slate-500 dark:text-slate-400' },
  
  { match: /(?<!dark:)\bborder-slate-100\b/g, replace: 'border-slate-100 dark:border-slate-800' },
  { match: /(?<!dark:)\bborder-slate-200\b/g, replace: 'border-slate-200 dark:border-slate-700' },
  { match: /(?<!dark:)\bborder-slate-300\b/g, replace: 'border-slate-300 dark:border-slate-600' },
];

files.forEach(file => {
  const filePath = path.join(dir, file);
  let content = fs.readFileSync(filePath, 'utf8');
  
  replacements.forEach(({match, replace}) => {
    content = content.replace(match, replace);
  });
  
  fs.writeFileSync(filePath, content, 'utf8');
  console.log(`Updated ${file}`);
});
