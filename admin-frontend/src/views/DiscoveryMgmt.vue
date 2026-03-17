<script setup>
import { ref, onMounted } from 'vue'
import { useAdminStore } from '../stores/admin'
import api from '../api'

const store = useAdminStore()
const activeTab = ref('news')
const newsList = ref([])
const knowledgeList = ref([])
const qnaList = ref([])
const toast = ref('')

// QnA View state
const showQnaModal = ref(false)
const viewingQna = ref(null)

// News CRUD state
const showNewsModal = ref(false)
const editingNews = ref(null)
const newsForm = ref({ title: '', tag: '', content: '' })

// Knowledge CRUD state
const showKnowledgeModal = ref(false)
const editingKnowledge = ref(null)
const knowledgeForm = ref({ title: '', conditionType: '病害', tag: '', plantId: '', description: '', prevention: '' })

onMounted(() => { loadNews(); loadKnowledge(); loadQna() })

async function loadNews() {
  const { data } = await api.get('/discovery/news')
  if (data.code === 200) newsList.value = data.data
}
async function loadKnowledge() {
  const { data } = await api.get('/discovery/knowledge')
  if (data.code === 200) knowledgeList.value = data.data
}
async function loadQna() {
  const { data } = await api.get('/discovery/qna')
  if (data.code === 200) qnaList.value = data.data
}

// --- News CRUD ---
function openCreateNews() {
  editingNews.value = null
  newsForm.value = { title: '', tag: '', content: '' }
  showNewsModal.value = true
}

function openEditNews(item) {
  editingNews.value = item
  newsForm.value = { title: item.title, tag: item.tag || '', content: item.content || '' }
  showNewsModal.value = true
}

async function saveNews() {
  if (!newsForm.value.title) return
  if (editingNews.value) {
    await api.put(`/discovery/news/${editingNews.value.newsId}`, newsForm.value)
    showToast('资讯已更新')
  } else {
    await api.post('/discovery/news', { ...newsForm.value, authorId: 1 })
    showToast('资讯已创建')
  }
  showNewsModal.value = false
  loadNews()
}

async function deleteNews(id) {
  if (!confirm('确定删除该资讯？')) return
  await api.delete(`/discovery/news/${id}`)
  showToast('资讯已删除'); loadNews()
}

// --- Knowledge CRUD ---
function openCreateKnowledge() {
  editingKnowledge.value = null
  knowledgeForm.value = { title: '', conditionType: '病害', tag: '', plantId: '', description: '', prevention: '' }
  showKnowledgeModal.value = true
}

function openEditKnowledge(item) {
  editingKnowledge.value = item
  knowledgeForm.value = {
    title: item.title,
    conditionType: item.conditionType || '病害',
    tag: item.tag || '',
    plantId: item.plantId || '',
    description: item.description || '',
    prevention: item.prevention || ''
  }
  showKnowledgeModal.value = true
}

async function saveKnowledge() {
  if (!knowledgeForm.value.title) return
  if (editingKnowledge.value) {
    await api.put(`/discovery/knowledge/${editingKnowledge.value.knowledgeId}`, knowledgeForm.value)
    showToast('知识条目已更新')
  } else {
    await api.post('/discovery/knowledge', knowledgeForm.value)
    showToast('知识条目已创建')
  }
  showKnowledgeModal.value = false
  loadKnowledge()
}

async function deleteKnowledge(id) {
  if (!confirm('确定删除该知识条目？')) return
  await api.delete(`/discovery/knowledge/${id}`)
  showToast('知识条目已删除'); loadKnowledge()
}

// --- QnA Review ---
async function reviewQna(postId, status) {
  const action = status === 1 ? '通过' : '拒绝'
  if (!confirm(`确定${action}该帖子？`)) return
  await api.put(`/discovery/qna/${postId}/review`, { status })
  showToast(`帖子已${action}`)
  loadQna()
}

async function deleteQna(id) {
  if (!confirm('确定删除该帖子？')) return
  await api.delete(`/discovery/qna/${id}`)
  showToast('帖子已删除'); loadQna()
}

function openViewQna(qna) {
  viewingQna.value = qna
  showQnaModal.value = true
}

function statusLabel(s) {
  return { 0: '待审核', 1: '已通过', 2: '已拒绝' }[s] ?? '待审核'
}
function statusColor(s) {
  return { 0: 'yellow', 1: 'green', 2: 'red' }[s] ?? 'gray'
}

function showToast(msg) { toast.value = msg; setTimeout(() => toast.value = '', 2500) }
</script>

<template>
  <div>
    <div class="page-header">
      <h2>🔍 发现管理</h2>
      <p>管理资讯推荐、知识库和问答圈内容</p>
    </div>

    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'news' }" @click="activeTab = 'news'">📰 资讯推荐</button>
      <button class="tab-btn" :class="{ active: activeTab === 'knowledge' }" @click="activeTab = 'knowledge'">📚 知识库</button>
      <button class="tab-btn" :class="{ active: activeTab === 'qna' }" @click="activeTab = 'qna'">💬 问答圈</button>
    </div>

    <!-- ========== 资讯 ========== -->
    <div v-if="activeTab === 'news'">
      <div class="toolbar">
        <div class="toolbar-left">共 {{ newsList.length }} 条资讯</div>
        <div class="toolbar-right">
          <button class="btn btn-primary" @click="openCreateNews">＋ 新建资讯</button>
        </div>
      </div>
      <div class="table-wrapper">
        <table>
          <thead><tr><th>ID</th><th>标题</th><th>标签</th><th>浏览量</th><th>发布时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="n in newsList" :key="n.newsId">
              <td>{{ n.newsId }}</td>
              <td class="text-truncate">{{ n.title }}</td>
              <td><span class="badge blue">{{ n.tag || '—' }}</span></td>
              <td>{{ n.views }}</td>
              <td>{{ n.createdAt }}</td>
              <td>
                <div class="btn-group">
                  <button class="btn btn-ghost btn-sm" @click="openEditNews(n)">编辑</button>
                  <button class="btn btn-danger btn-sm" @click="deleteNews(n.newsId)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!newsList.length"><td colspan="6" class="text-center" style="padding:40px;color:var(--text-muted)">暂无资讯</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ========== 知识库 ========== -->
    <div v-if="activeTab === 'knowledge'">
      <div class="toolbar">
        <div class="toolbar-left">共 {{ knowledgeList.length }} 条知识</div>
        <div class="toolbar-right">
          <button class="btn btn-primary" @click="openCreateKnowledge">＋ 新建知识</button>
        </div>
      </div>
      <div class="table-wrapper">
        <table>
          <thead><tr><th>ID</th><th>标题</th><th>植物</th><th>类型</th><th>标签</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="k in knowledgeList" :key="k.knowledgeId">
              <td>{{ k.knowledgeId }}</td>
              <td class="text-truncate">{{ k.title }}</td>
              <td>{{ k.plantName || '—' }}</td>
              <td><span class="badge green">{{ k.conditionType }}</span></td>
              <td>{{ k.tag || '—' }}</td>
              <td>
                <div class="btn-group">
                  <button class="btn btn-ghost btn-sm" @click="openEditKnowledge(k)">编辑</button>
                  <button class="btn btn-danger btn-sm" @click="deleteKnowledge(k.knowledgeId)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!knowledgeList.length"><td colspan="6" class="text-center" style="padding:40px;color:var(--text-muted)">暂无知识条目</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ========== 问答圈 ========== -->
    <div v-if="activeTab === 'qna'">
      <div class="toolbar">
        <div class="toolbar-left">共 {{ qnaList.length }} 条帖子</div>
      </div>
      <div class="table-wrapper">
        <table>
          <thead><tr><th>ID</th><th>内容</th><th>发帖人</th><th>点赞</th><th>时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="q in qnaList" :key="q.postId">
              <td>{{ q.postId }}</td>
              <td class="text-truncate">{{ q.content }}</td>
              <td>{{ q.userName || '—' }}</td>
              <td>{{ q.likes }}</td>
              <td>{{ q.createdAt }}</td>
              <td>
                <div class="btn-group">
                  <button class="btn btn-ghost btn-sm" @click="openViewQna(q)">查看</button>
                  <button class="btn btn-danger btn-sm" @click="deleteQna(q.postId)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!qnaList.length"><td colspan="6" class="text-center" style="padding:40px;color:var(--text-muted)">暂无帖子</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- News Modal -->
    <div v-if="showNewsModal" class="modal-overlay" @click.self="showNewsModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editingNews ? '编辑资讯' : '新建资讯' }}</h3>
          <button class="modal-close" @click="showNewsModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>资讯标题</label>
            <input v-model="newsForm.title" type="text" placeholder="请输入标题" />
          </div>
          <div class="form-group">
            <label>标签</label>
            <input v-model="newsForm.tag" type="text" placeholder="如：病虫害防治、农业科技" />
          </div>
          <div class="form-group">
            <label>资讯内容</label>
            <textarea v-model="newsForm.content" rows="8" placeholder="请输入资讯正文"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showNewsModal = false">取消</button>
          <button class="btn btn-primary" @click="saveNews">{{ editingNews ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- Knowledge Modal -->
    <div v-if="showKnowledgeModal" class="modal-overlay" @click.self="showKnowledgeModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editingKnowledge ? '编辑知识条目' : '新建知识条目' }}</h3>
          <button class="modal-close" @click="showKnowledgeModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>标题</label>
            <input v-model="knowledgeForm.title" type="text" placeholder="请输入知识标题" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>类型</label>
              <select v-model="knowledgeForm.conditionType">
                <option value="病害">病害</option>
                <option value="虫害">虫害</option>
                <option value="缺素">缺素</option>
                <option value="其他">其他</option>
              </select>
            </div>
            <div class="form-group">
              <label>标签</label>
              <input v-model="knowledgeForm.tag" type="text" placeholder="如：常见病害、预防知识" />
            </div>
          </div>
          <div class="form-group">
            <label>关联植物ID（可选）</label>
            <input v-model="knowledgeForm.plantId" type="number" placeholder="如：1" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="knowledgeForm.description" rows="4" placeholder="请输入知识描述"></textarea>
          </div>
          <div class="form-group">
            <label>防治措施（JSON数组格式）</label>
            <textarea v-model="knowledgeForm.prevention" rows="3" placeholder='如：["喷洒杀虫剂","加强通风"]'></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showKnowledgeModal = false">取消</button>
          <button class="btn btn-primary" @click="saveKnowledge">{{ editingKnowledge ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- QnA View Modal -->
    <div v-if="showQnaModal" class="modal-overlay" @click.self="showQnaModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>帖子详情</h3>
          <button class="modal-close" @click="showQnaModal = false">×</button>
        </div>
        <div class="modal-body" v-if="viewingQna">
          <div style="margin-bottom: 16px;">
            <strong>发帖人：</strong> {{ viewingQna.userName || '—' }}
            <span style="color: var(--text-muted); font-size: 13px; margin-left: 12px;">{{ viewingQna.createdAt }}</span>
          </div>
          <div style="background: var(--bg-hover); padding: 16px; border-radius: 8px; white-space: pre-wrap; line-height: 1.6; margin-bottom: 16px;">
            {{ viewingQna.content }}
          </div>
          <div v-if="viewingQna.images" style="margin-bottom: 16px;">
            <strong>图片：</strong>
            <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px;">
              <img v-for="(img, idx) in (typeof viewingQna.images === 'string' ? JSON.parse(viewingQna.images || '[]') : viewingQna.images)" 
                   :key="idx" 
                   :src="img.startsWith('http') ? img : 'http://localhost:5173' + img" 
                   style="max-width: 200px; max-height: 200px; object-fit: cover; border-radius: 4px; border: 1px solid var(--border-color);" />
            </div>
          </div>
          <div>
            <strong>点赞数：</strong> <span class="badge blue">{{ viewingQna.likes }}</span>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-primary" @click="showQnaModal = false">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast-container"><div class="toast success">{{ toast }}</div></div>
  </div>
</template>
