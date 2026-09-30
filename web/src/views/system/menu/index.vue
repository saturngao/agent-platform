<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="菜单名称">
          <el-input v-model="queryParams.menuName" placeholder="请输入菜单名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div>
          <el-button type="primary" icon="Plus" @click="handleAdd()">新增</el-button>
          <el-button icon="Sort" @click="toggleExpandAll">{{ expandAll ? '收起全部' : '展开全部' }}</el-button>
        </div>
      </div>

      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="list"
        row-key="menuId"
        :default-expand-all="expandAll"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column label="菜单名称" prop="menuName" min-width="180" show-overflow-tooltip />
        <el-table-column label="图标" width="70" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="orderNum" width="70" align="center" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.menuType === 'M'" size="small">目录</el-tag>
            <el-tag v-else-if="row.menuType === 'C'" size="small" type="success">菜单</el-tag>
            <el-tag v-else size="small" type="warning">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限标识" prop="perms" min-width="160" show-overflow-tooltip />
        <el-table-column label="组件路径" prop="component" min-width="180" show-overflow-tooltip />
        <el-table-column label="路由地址" prop="path" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="primary" icon="Plus" @click="handleAdd(row)">新增</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无菜单数据</div>
        </template>
      </el-table>
    </div>

    <!-- 新增/修改 -->
    <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="menuTreeOptions"
            :props="{ value: 'menuId', label: 'menuName', children: 'children' }"
            node-key="menuId"
            check-strictly
            placeholder="选择上级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio label="M">目录</el-radio>
            <el-radio label="C">菜单</el-radio>
            <el-radio label="F">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="菜单图标">
              <el-select v-model="form.icon" placeholder="请选择图标" clearable filterable style="width: 100%">
                <template #prefix>
                  <el-icon v-if="form.icon"><component :is="form.icon" /></el-icon>
                </template>
                <el-option v-for="name in iconOptions" :key="name" :label="name" :value="name">
                  <div style="display: flex; align-items: center; gap: 8px">
                    <el-icon><component :is="name" /></el-icon>
                    <span>{{ name }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="路由地址">
              <el-input v-model="form.path" placeholder="请输入路由地址，如 user" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item label="组件路径">
              <el-input v-model="form.component" placeholder="如 system/user/index" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'M'" :span="12">
            <el-form-item label="权限标识">
              <el-input v-model="form.perms" placeholder="如 system:user:list" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item label="路由参数">
              <el-input v-model="form.query" placeholder='如 {"id": 1}' />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item label="是否缓存">
              <el-radio-group v-model="form.isCache">
                <el-radio label="0">缓存</el-radio>
                <el-radio label="1">不缓存</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="显示状态">
              <el-radio-group v-model="form.visible">
                <el-radio label="0">显示</el-radio>
                <el-radio label="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单状态">
              <el-radio-group v-model="form.status">
                <el-radio label="0">正常</el-radio>
                <el-radio label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMenu, getMenu, addMenu, updateMenu, delMenu, menuTree } from '@/api/system'
import { buildTree } from '@/utils/index'

/** 常用图标（Element Plus 图标库） */
const iconOptions = [
  'HomeFilled', 'Menu', 'Setting', 'Tools', 'User', 'UserFilled', 'Avatar', 'Grid', 'List',
  'Document', 'Files', 'Folder', 'FolderOpened', 'Tickets', 'DataAnalysis', 'DataLine', 'Histogram',
  'PieChart', 'TrendCharts', 'Monitor', 'Cpu', 'Connection', 'Share', 'Promotion', 'MagicStick',
  'Message', 'Bell', 'Notification', 'Star', 'Search', 'Edit', 'View', 'Lock', 'Key', 'Clock',
  'Coin', 'Aim', 'Compass', 'Collection', 'Box', 'OfficeBuilding', 'Management', 'Operation'
]

const loading = ref(false)
const submitLoading = ref(false)
const open = ref(false)
const title = ref('')
const list = ref([])
const refreshTable = ref(true)
const expandAll = ref(true)
const menuTreeOptions = ref([])
const formRef = ref()

const queryParams = reactive({
  menuName: '',
  status: ''
})

const defaultForm = () => ({
  menuId: null,
  parentId: 0,
  menuName: '',
  orderNum: 1,
  path: '',
  component: '',
  query: '',
  isFrame: '1',
  isCache: '0',
  menuType: 'M',
  visible: '0',
  status: '0',
  perms: '',
  icon: ''
})
const form = ref(defaultForm())

const rules = {
  menuName: [{ required: true, message: '菜单名称不能为空', trigger: 'blur' }],
  orderNum: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
}

function normalizeMenu(nodes = []) {
  return (nodes || []).map((node) => ({
    menuId: node.menuId ?? node.id,
    menuName: node.menuName ?? node.label,
    children: node.children?.length ? normalizeMenu(node.children) : []
  }))
}

async function getList() {
  loading.value = true
  try {
    const res = await listMenu(queryParams)
    const rows = Array.isArray(res) ? res : res?.rows || []
    // 后端返回平铺列表时前端自行组装树，已是树形则直接使用
    const isTree = rows.some((row) => row.children && row.children.length)
    if (!rows.length) {
      list.value = normalizeMenu(await menuTree().catch(() => []))
    } else {
      list.value = isTree ? rows : buildTree(rows, { idKey: 'menuId', parentKey: 'parentId' })
    }
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadMenuTree() {
  try {
    const res = await menuTree()
    const tree = normalizeMenu(res)
    menuTreeOptions.value = [{ menuId: 0, menuName: '主类目', children: tree }]
  } catch (e) {
    menuTreeOptions.value = [{ menuId: 0, menuName: '主类目', children: [] }]
  }
}

function resetQuery() {
  queryParams.menuName = ''
  queryParams.status = ''
  getList()
}

async function toggleExpandAll() {
  refreshTable.value = false
  expandAll.value = !expandAll.value
  await nextTick()
  refreshTable.value = true
}

async function handleAdd(row) {
  form.value = defaultForm()
  if (row && row.menuId != null) {
    form.value.parentId = row.menuId
  }
  title.value = '新增菜单'
  open.value = true
}

async function handleUpdate(row) {
  let detail = { ...row }
  try {
    const res = await getMenu(row.menuId)
    if (res) detail = { ...row, ...res }
  } catch (e) {
    /* 使用行数据 */
  }
  form.value = { ...defaultForm(), ...detail }
  title.value = '修改菜单'
  open.value = true
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.value.menuId != null) {
        await updateMenu(form.value)
        ElMessage.success('修改成功')
      } else {
        await addMenu(form.value)
        ElMessage.success('新增成功')
      }
      open.value = false
      loadMenuTree()
      getList()
    } catch (e) {
      /* 统一提示 */
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`是否确认删除菜单「${row.menuName}」及其子菜单？`, '警告', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await delMenu(row.menuId)
  ElMessage.success('删除成功')
  loadMenuTree()
  getList()
}

onMounted(() => {
  loadMenuTree()
  getList()
})
</script>
