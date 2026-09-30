<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="部门名称">
          <el-input v-model="queryParams.deptName" placeholder="请输入部门名称" clearable style="width: 180px" />
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
        <div class="table-toolbar__right">
          <el-tooltip content="刷新" placement="top">
            <el-button icon="Refresh" circle @click="getList" />
          </el-tooltip>
        </div>
      </div>

      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="list"
        row-key="deptId"
        :default-expand-all="expandAll"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column label="部门名称" prop="deptName" min-width="200" show-overflow-tooltip />
        <el-table-column label="排序" prop="orderNum" width="80" align="center" />
        <el-table-column label="负责人" prop="leader" width="110" />
        <el-table-column label="电话" prop="phone" width="140" />
        <el-table-column label="邮箱" prop="email" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170">
          <template #default="{ row }">{{ parseTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="primary" icon="Plus" @click="handleAdd(row)">新增</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无部门数据</div>
        </template>
      </el-table>
    </div>

    <!-- 新增/修改 -->
    <el-dialog v-model="open" :title="title" width="640px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="deptTreeOptions"
            :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
            node-key="deptId"
            check-strictly
            placeholder="选择上级部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门名称" prop="deptName">
              <el-input v-model="form.deptName" placeholder="请输入部门名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.leader" placeholder="请输入负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门状态">
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
import { listDept, getDept, addDept, updateDept, delDept, deptTree } from '@/api/system'
import { parseTime, buildTree } from '@/utils/index'

const loading = ref(false)
const submitLoading = ref(false)
const open = ref(false)
const title = ref('')
const list = ref([])
const refreshTable = ref(true)
const expandAll = ref(true)
const deptTreeOptions = ref([])
const formRef = ref()

const queryParams = reactive({
  deptName: '',
  status: ''
})

const defaultForm = () => ({
  deptId: null,
  parentId: 0,
  deptName: '',
  orderNum: 1,
  leader: '',
  phone: '',
  email: '',
  status: '0'
})
const form = ref(defaultForm())

const rules = {
  deptName: [{ required: true, message: '部门名称不能为空', trigger: 'blur' }],
  orderNum: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }]
}

function normalizeDept(nodes = []) {
  return (nodes || []).map((node) => ({
    deptId: node.deptId ?? node.id,
    deptName: node.deptName ?? node.label,
    children: node.children?.length ? normalizeDept(node.children) : []
  }))
}

async function getList() {
  loading.value = true
  try {
    const res = await listDept(queryParams)
    const rows = Array.isArray(res) ? res : res?.rows || []
    // 后端返回平铺列表时前端自行组装树，已是树形则直接使用
    const isTree = rows.some((row) => row.children && row.children.length)
    if (!rows.length) {
      list.value = normalizeDept(await deptTree().catch(() => []))
    } else {
      list.value = isTree ? rows : buildTree(rows, { idKey: 'deptId', parentKey: 'parentId' })
    }
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadDeptTree() {
  try {
    const tree = normalizeDept(await deptTree())
    deptTreeOptions.value = [{ deptId: 0, deptName: '顶级部门', children: tree }]
  } catch (e) {
    deptTreeOptions.value = [{ deptId: 0, deptName: '顶级部门', children: [] }]
  }
}

function resetQuery() {
  queryParams.deptName = ''
  queryParams.status = ''
  getList()
}

async function toggleExpandAll() {
  refreshTable.value = false
  expandAll.value = !expandAll.value
  await nextTick()
  refreshTable.value = true
}

function handleAdd(row) {
  form.value = defaultForm()
  if (row && row.deptId != null) {
    form.value.parentId = row.deptId
  }
  title.value = '新增部门'
  open.value = true
}

async function handleUpdate(row) {
  let detail = { ...row }
  try {
    const res = await getDept(row.deptId)
    if (res) detail = { ...row, ...res }
  } catch (e) {
    /* 使用行数据 */
  }
  form.value = { ...defaultForm(), ...detail }
  title.value = '修改部门'
  open.value = true
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.value.deptId != null) {
        await updateDept(form.value)
        ElMessage.success('修改成功')
      } else {
        await addDept(form.value)
        ElMessage.success('新增成功')
      }
      open.value = false
      loadDeptTree()
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
    await ElMessageBox.confirm(`是否确认删除部门「${row.deptName}」？`, '警告', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await delDept(row.deptId)
  ElMessage.success('删除成功')
  loadDeptTree()
  getList()
}

onMounted(() => {
  loadDeptTree()
  getList()
})
</script>
