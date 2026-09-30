<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="角色名称">
          <el-input v-model="queryParams.roleName" placeholder="请输入角色名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="权限字符">
          <el-input v-model="queryParams.roleKey" placeholder="请输入权限字符" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div>
          <el-button type="primary" icon="Plus" @click="handleAdd">新增</el-button>
          <el-button type="danger" icon="Delete" :disabled="ids.length === 0" @click="handleDelete()">
            删除
          </el-button>
        </div>
        <div class="table-toolbar__right">
          <el-tooltip content="刷新" placement="top">
            <el-button icon="Refresh" circle @click="getList" />
          </el-tooltip>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" row-key="roleId" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="角色编号" prop="roleId" width="90" align="center" />
        <el-table-column label="角色名称" prop="roleName" min-width="140" show-overflow-tooltip />
        <el-table-column label="权限字符" prop="roleKey" min-width="140" show-overflow-tooltip />
        <el-table-column label="显示顺序" prop="roleSort" width="100" align="center" />
        <el-table-column label="数据范围" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ dataScopeLabel(row.dataScope) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <!--
              不用 v-model 的原因同用户管理页：若 active-value 写成字符串 "0"
              而后端返回数字 0，el-switch 会在渲染时自动 emit change，
              导致一进页面就弹出停用确认框。改由 :model-value 派生状态。
            -->
            <el-switch
              :model-value="row.status === 0"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170">
          <template #default="{ row }">{{ parseTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
            <el-button link type="success" icon="Menu" @click="openMenuAuth(row)">菜单权限</el-button>
            <el-button link type="warning" icon="Lock" @click="openDataScope(row)">数据权限</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无角色数据</div>
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </div>

    <!-- 新增/修改 -->
    <el-dialog v-model="open" :title="title" width="560px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="权限字符" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="请输入权限字符，如 admin" />
        </el-form-item>
        <el-form-item label="显示顺序" prop="roleSort">
          <el-input-number v-model="form.roleSort" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="菜单权限">
          <el-tree
            ref="menuTreeRef"
            :data="menuOptions"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            show-checkbox
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 菜单权限分配 -->
    <el-dialog v-model="menuAuthOpen" title="分配菜单权限" width="520px" append-to-body destroy-on-close>
      <el-tree
        ref="authTreeRef"
        :data="menuOptions"
        :props="{ label: 'label', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="menuAuthOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitMenuAuth">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 数据权限 -->
    <el-dialog v-model="dataScopeOpen" title="分配数据权限" width="560px" append-to-body destroy-on-close>
      <el-form :model="dataScopeForm" label-width="96px">
        <el-form-item label="角色名称">
          <el-input v-model="dataScopeForm.roleName" disabled />
        </el-form-item>
        <el-form-item label="权限字符">
          <el-input v-model="dataScopeForm.roleKey" disabled />
        </el-form-item>
        <el-form-item label="权限范围">
          <el-radio-group v-model="dataScopeForm.dataScope">
            <el-radio v-for="item in dataScopeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="dataScopeForm.dataScope === '2'" label="数据范围">
          <el-tree
            ref="deptTreeRef"
            :data="deptOptions"
            :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
            node-key="deptId"
            show-checkbox
            default-expand-all
            :default-checked-keys="checkedDeptKeys"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dataScopeOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitDataScope">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRole,
  addRole,
  updateRole,
  delRole,
  changeRoleStatus,
  updateRoleDataScope,
  roleMenuTree,
  roleDeptTree,
  menuTree,
  deptTree
} from '@/api/system'
import { parseTime, addDateRange } from '@/utils/index'

const loading = ref(false)
const submitLoading = ref(false)
const open = ref(false)
const title = ref('')
const list = ref([])
const total = ref(0)
const ids = ref([])
const dateRange = ref([])
const menuOptions = ref([])
const deptOptions = ref([])

const formRef = ref()
const menuTreeRef = ref()
const authTreeRef = ref()
const deptTreeRef = ref()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  roleName: '',
  roleKey: '',
  status: ''
})

const defaultForm = () => ({
  roleId: null,
  roleName: '',
  roleKey: '',
  roleSort: 1,
  status: 0,
  menuIds: [],
  remark: ''
})
const form = ref(defaultForm())

const rules = {
  roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  roleKey: [{ required: true, message: '权限字符不能为空', trigger: 'blur' }],
  roleSort: [{ required: true, message: '显示顺序不能为空', trigger: 'blur' }]
}

const dataScopeOptions = [
  { value: '1', label: '全部数据权限' },
  { value: '2', label: '自定数据权限' },
  { value: '3', label: '本部门数据权限' },
  { value: '4', label: '本部门及以下数据权限' },
  { value: '5', label: '仅本人数据权限' }
]
const dataScopeLabel = (value) => dataScopeOptions.find((i) => i.value === String(value))?.label || '-'

/* ------------------------------ 菜单权限弹窗 ------------------------------ */
const menuAuthOpen = ref(false)
const currentRoleId = ref(null)

/* ------------------------------ 数据权限弹窗 ------------------------------ */
const dataScopeOpen = ref(false)
const checkedDeptKeys = ref([])
const dataScopeForm = reactive({
  roleId: null,
  roleName: '',
  roleKey: '',
  dataScope: '1',
  deptIds: []
})

/** 菜单树节点 key 统一为 id */
function normalizeMenu(nodes = []) {
  return (nodes || []).map((node) => ({
    id: node.id ?? node.menuId,
    label: node.label ?? node.menuName,
    children: node.children?.length ? normalizeMenu(node.children) : []
  }))
}

function normalizeDept(nodes = []) {
  return (nodes || []).map((node) => ({
    deptId: node.deptId ?? node.id,
    deptName: node.deptName ?? node.label,
    children: node.children?.length ? normalizeDept(node.children) : []
  }))
}

const menuIdsFromTree = (treeRef) => [
  ...(treeRef?.getCheckedKeys() || []),
  ...(treeRef?.getHalfCheckedKeys() || [])
]

/**
 * 回显前过滤出"叶子节点"ID。
 * 数据库里存的是全量 ID（含半选父节点，保存时 getHalfCheckedKeys 一并存了），
 * 而 el-tree 默认父子联动：setCheckedKeys 传父节点 ID 会把它所有子节点全部勾上，
 * 导致回显权限被放大。只 set 叶子节点，父节点会根据子节点自动呈现半选/全选。
 * @param {Array} checkedIds 后端返回的已授权 ID 数组
 * @param {Array} nodes 树数据（菜单树或部门树）
 * @param {Function} idOf 取节点 ID 的函数
 */
function leafCheckedKeys(checkedIds = [], nodes = menuOptions.value, idOf = (n) => n.id) {
  const idSet = new Set((checkedIds || []).map(Number))
  const leafs = []
  const walk = (list = []) => {
    list.forEach((n) => {
      if (n.children?.length) {
        walk(n.children)
      } else if (idSet.has(Number(idOf(n)))) {
        leafs.push(idOf(n))
      }
    })
  }
  walk(nodes)
  return leafs
}

/** 取角色已授权菜单 + 全量授权树（含按钮层） */
async function fetchCheckedMenuIds(roleId, fallback = []) {
  try {
    const res = await roleMenuTree(roleId)
    if (res?.menus?.length) menuOptions.value = normalizeMenu(res.menus)
    return res?.checkedKeys || fallback
  } catch (e) {
    return fallback
  }
}

async function getList() {
  loading.value = true
  try {
    const res = await listRole(addDateRange(queryParams, dateRange.value))
    // status 归一化为数字，避免后端偶发返回字符串导致开关状态判断错误
    list.value = (res?.rows || []).map((r) => ({ ...r, status: Number(r.status) }))
    total.value = res?.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.roleName = ''
  queryParams.roleKey = ''
  queryParams.status = ''
  queryParams.pageNum = 1
  dateRange.value = []
  getList()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.roleId)
}

async function loadTrees() {
  try {
    menuOptions.value = normalizeMenu(await menuTree())
  } catch (e) {
    menuOptions.value = []
  }
  try {
    deptOptions.value = normalizeDept(await deptTree())
  } catch (e) {
    deptOptions.value = []
  }
}

async function handleAdd() {
  form.value = defaultForm()
  title.value = '新增角色'
  open.value = true
  await nextTick()
  menuTreeRef.value?.setCheckedKeys([])
}

async function handleUpdate(row) {
  title.value = '修改角色'
  form.value = { ...defaultForm(), ...row, menuIds: row.menuIds || [] }
  open.value = true
  // 先查已授权菜单 ID（后端返回数组），等树渲染完再回显叶子节点
  const checkedIds = await fetchCheckedMenuIds(row.roleId, row.menuIds || [])
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(leafCheckedKeys(checkedIds))
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const menuIds = menuIdsFromTree(menuTreeRef.value)
      const payload = { ...form.value, menuIds }
      if (payload.roleId != null) {
        await updateRole(payload)
        ElMessage.success('修改成功')
      } else {
        await addRole(payload)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
    } catch (e) {
      /* 统一提示 */
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  const roleIds = row?.roleId ? String(row.roleId) : ids.value.join(',')
  if (!roleIds) return
  try {
    await ElMessageBox.confirm(
      row?.roleId ? `是否确认删除角色「${row.roleName}」？` : `是否确认删除选中的 ${ids.value.length} 条数据？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delRole(roleIds)
  ElMessage.success('删除成功')
  getList()
}

/**
 * 启停角色
 * @param {object} row 行数据
 * @param {boolean} val 开关目标值：true=正常(0)，false=停用(1)
 */
async function handleStatusChange(row, val) {
  const next = val ? 0 : 1
  const text = val ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认要${text}「${row.roleName}」角色吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    // 取消：不改 row.status，开关自动回到原状
    return
  }
  const prev = row.status
  row.status = next
  try {
    await changeRoleStatus({ roleId: row.roleId, status: next })
    ElMessage.success(`${text}成功`)
  } catch (e) {
    row.status = prev
  }
}

async function openMenuAuth(row) {
  currentRoleId.value = row.roleId
  menuAuthOpen.value = true
  const checkedIds = await fetchCheckedMenuIds(row.roleId, row.menuIds || [])
  await nextTick()
  authTreeRef.value?.setCheckedKeys(leafCheckedKeys(checkedIds))
}

async function submitMenuAuth() {
  submitLoading.value = true
  try {
    const menuIds = menuIdsFromTree(authTreeRef.value)
    await updateRole({ roleId: currentRoleId.value, menuIds })
    ElMessage.success('菜单权限分配成功')
    menuAuthOpen.value = false
    getList()
  } catch (e) {
    /* 统一提示 */
  } finally {
    submitLoading.value = false
  }
}

async function openDataScope(row) {
  dataScopeForm.roleId = row.roleId
  dataScopeForm.roleName = row.roleName
  dataScopeForm.roleKey = row.roleKey
  dataScopeForm.dataScope = String(row.dataScope || '1')
  dataScopeOpen.value = true
  // 查角色已授权部门 ID（后端返回 Long[] 数组），等树渲染完再回显叶子节点
  let checkedDeptIds = []
  try {
    const res = await roleDeptTree(row.roleId)
    checkedDeptIds = Array.isArray(res) ? res : (res?.checkedDeptKeys || [])
  } catch (e) {
    checkedDeptIds = []
  }
  await nextTick()
  deptTreeRef.value?.setCheckedKeys(leafCheckedKeys(checkedDeptIds, deptOptions.value, (n) => n.deptId))
}

async function submitDataScope() {
  submitLoading.value = true
  try {
    const deptIds =
      dataScopeForm.dataScope === '2'
        ? [
            ...(deptTreeRef.value?.getCheckedKeys() || []),
            ...(deptTreeRef.value?.getHalfCheckedKeys() || [])
          ]
        : []
    await updateRoleDataScope({ ...dataScopeForm, deptIds })
    ElMessage.success('数据权限分配成功')
    dataScopeOpen.value = false
    getList()
  } catch (e) {
    /* 统一提示 */
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadTrees()
  getList()
})
</script>

<style scoped lang="scss">
:deep(.el-radio-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
