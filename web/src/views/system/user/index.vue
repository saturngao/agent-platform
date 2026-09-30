<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="page-card">
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="queryParams.deptId"
            :data="deptOptions"
            :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
            value-key="deptId"
            node-key="deptId"
            check-strictly
            placeholder="请选择部门"
            clearable
            style="width: 200px"
          />
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

      <!-- 工具条 -->
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

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="list"
        row-key="userId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="用户编号" prop="userId" width="90" align="center" />
        <el-table-column label="用户名" prop="username" min-width="110" show-overflow-tooltip />
        <el-table-column label="昵称" prop="nickname" min-width="110" show-overflow-tooltip />
        <el-table-column label="部门" prop="deptName" min-width="120" show-overflow-tooltip />
        <el-table-column label="手机号" prop="phonenumber" width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <!--
              注意：这里刻意不用 v-model。
              el-switch 用 v-model 时，若 modelValue 与 active/inactive-value 类型不一致
              （后端返回数字 0/1，而这里若写 active-value="0" 则是字符串），
              组件会在渲染时自动 emit 一次 change 来"修正"值，
              导致一进页面就弹出 N 个停用确认框。
              改用 :model-value + 布尔判断，开关状态由 row.status 派生，
              确认成功后再写回 row.status，取消/失败则自动保持原状。
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
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
            <el-button link type="warning" icon="Key" @click="handleResetPwd(row)">重置</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无用户数据</div>
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

    <!-- 新增/修改弹窗 -->
    <el-dialog v-model="open" :title="title" width="680px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" :disabled="form.userId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.userId == null" label="密码" prop="password">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" placeholder="请输入昵称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptOptions"
                :props="{ value: 'deptId', label: 'deptName', children: 'children' }"
                node-key="deptId"
                check-strictly
                placeholder="请选择归属部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phonenumber">
              <el-input v-model="form.phonenumber" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.sex" placeholder="请选择性别" style="width: 100%">
                <el-option v-for="item in sexOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :label="0">正常</el-radio>
                <el-radio :label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
                <el-option
                  v-for="item in roleOptions"
                  :key="item.roleId"
                  :label="item.roleName"
                  :value="item.roleId"
                  :disabled="Number(item.status) === 1"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="form.postIds" multiple placeholder="请选择岗位" style="width: 100%">
                <el-option
                  v-for="item in postOptions"
                  :key="item.postId"
                  :label="item.postName"
                  :value="item.postId"
                  :disabled="Number(item.status) === 1"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listUser,
  getUserDetail,
  addUser,
  updateUser,
  delUser,
  resetUserPwd,
  changeUserStatus,
  deptTree,
  listRole,
  listPost
} from '@/api/system'
import { parseTime, addDateRange } from '@/utils/index'
import { useDict } from '@/utils/dict'

const { sys_user_sex } = useDict('sys_user_sex')

/** 部门树：兼容后端返回 deptId 或 id 两种字段 */
function normalizeDept(nodes = []) {
  return (nodes || []).map((node) => ({
    deptId: node.deptId ?? node.id,
    deptName: node.deptName ?? node.label,
    children: node.children?.length ? normalizeDept(node.children) : []
  }))
}

const loading = ref(false)
const submitLoading = ref(false)
const open = ref(false)
const title = ref('')
const list = ref([])
const total = ref(0)
const ids = ref([])
const dateRange = ref([])
const deptOptions = ref([])
const roleOptions = ref([])
const postOptions = ref([])

/** 性别选项：优先用字典 sys_user_sex，字典不可用时走内置兜底 */
const sexOptions = computed(() => {
  if (sys_user_sex.value && sys_user_sex.value.length) {
    return sys_user_sex.value.map((item) => ({ label: item.label, value: item.value }))
  }
  return [
    { label: '未知', value: '0' },
    { label: '男', value: '1' },
    { label: '女', value: '2' }
  ]
})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  phonenumber: '',
  status: '',
  deptId: undefined
})

const formRef = ref()
const defaultForm = () => ({
  userId: null,
  username: '',
  password: '',
  nickname: '',
  deptId: undefined,
  phonenumber: '',
  email: '',
  sex: '0',
  status: 0,
  roleIds: [],
  postIds: [],
  remark: ''
})
const form = ref(defaultForm())

const rules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 5, max: 20, message: '长度在 5 到 20 个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  phonenumber: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }]
}

/** 加载列表 */
async function getList() {
  loading.value = true
  try {
    const res = await listUser(addDateRange(queryParams, dateRange.value))
    // status 统一归一化为数字，避免后端偶尔返回字符串导致开关状态判断异常
    list.value = (res?.rows || []).map((u) => ({ ...u, status: Number(u.status) }))
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
  queryParams.username = ''
  queryParams.phonenumber = ''
  queryParams.status = ''
  queryParams.deptId = undefined
  queryParams.pageNum = 1
  dateRange.value = []
  getList()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.userId)
}

async function loadOptions() {
  try {
    const deptRes = await deptTree()
    deptOptions.value = normalizeDept(deptRes)
  } catch (e) {
    deptOptions.value = []
  }
  try {
    const roleRes = await listRole({ pageNum: 1, pageSize: 1000 })
    roleOptions.value = roleRes?.rows || []
  } catch (e) {
    roleOptions.value = []
  }
  try {
    const postRes = await listPost({ pageNum: 1, pageSize: 1000 })
    postOptions.value = postRes?.rows || []
  } catch (e) {
    postOptions.value = []
  }
}

function handleAdd() {
  form.value = defaultForm()
  if (!form.value.password) form.value.password = '123456'
  title.value = '新增用户'
  open.value = true
}

async function handleUpdate(row) {
  title.value = '修改用户'
  open.value = true
  // 后端提供 /system/user/detail/{userId}，取不到时直接用行数据兜底
  let detail = { ...row }
  try {
    const res = await getUserDetail(row.userId)
    if (res) detail = { ...row, ...res }
  } catch (e) {
    /* 使用行数据 */
  }
  form.value = {
    ...defaultForm(),
    ...detail,
    password: '',
    roleIds: detail.roleIds || [],
    postIds: detail.postIds || []
  }
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.value.userId != null) {
        await updateUser(form.value)
        ElMessage.success('修改成功')
      } else {
        await addUser(form.value)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
    } catch (e) {
      /* 错误已统一提示 */
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDelete(row) {
  const userIds = row?.userId ? String(row.userId) : ids.value.join(',')
  if (!userIds) return
  try {
    await ElMessageBox.confirm(
      row?.userId ? `是否确认删除用户「${row.username}」？` : `是否确认删除选中的 ${ids.value.length} 条数据？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delUser(userIds)
  ElMessage.success('删除成功')
  getList()
}

/**
 * 启停用户
 * @param {object} row 行数据
 * @param {boolean} val 开关目标值：true=正常(0)，false=停用(1)
 */
async function handleStatusChange(row, val) {
  const next = val ? 0 : 1
  const text = val ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认要${text}「${row.username}」用户吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    // 取消：不修改 row.status，开关由 :model-value 派生，自动回到原状
    return
  }
  const prev = row.status
  row.status = next
  try {
    await changeUserStatus({ userId: row.userId, status: next })
    ElMessage.success(`${text}成功`)
  } catch (e) {
    row.status = prev
  }
}

async function handleResetPwd(row) {
  try {
    const { value } = await ElMessageBox.prompt(`请输入「${row.username}」的新密码`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /^.{5,20}$/,
      inputErrorMessage: '密码长度需要在 5 到 20 个字符之间',
      inputValue: '123456'
    })
    await resetUserPwd({ userId: row.userId, password: value })
    ElMessage.success(`修改成功，新密码是：${value}`)
  } catch (e) {
    /* 用户取消或失败 */
  }
}

onMounted(() => {
  loadOptions()
  getList()
})
</script>

<style scoped lang="scss">
:deep(.el-table .cell) {
  font-size: 13px;
}
</style>
