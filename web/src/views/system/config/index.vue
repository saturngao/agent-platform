<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="参数名称">
          <el-input v-model="queryParams.configName" placeholder="请输入参数名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="参数键名">
          <el-input v-model="queryParams.configKey" placeholder="请输入参数键名" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-select v-model="queryParams.configType" placeholder="请选择" clearable style="width: 120px">
            <el-option label="是" value="Y" />
            <el-option label="否" value="N" />
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

      <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="参数主键" prop="configId" width="90" align="center" />
        <el-table-column label="参数名称" prop="configName" min-width="160" show-overflow-tooltip />
        <el-table-column label="参数键名" prop="configKey" min-width="160" show-overflow-tooltip />
        <el-table-column label="参数键值" prop="configValue" min-width="140" show-overflow-tooltip />
        <el-table-column label="系统内置" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configType === 'Y' ? 'primary' : 'info'" size="small">
              {{ row.configType === 'Y' ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="170">
          <template #default="{ row }">{{ parseTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无参数数据</div>
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入参数名称" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="form.configKey" placeholder="请输入参数键名" />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入参数键值" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-radio-group v-model="form.configType">
            <el-radio label="Y">是</el-radio>
            <el-radio label="N">否</el-radio>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listConfig, getConfig, addConfig, updateConfig, delConfig } from '@/api/system'
import { parseTime, addDateRange } from '@/utils/index'

const loading = ref(false)
const submitLoading = ref(false)
const open = ref(false)
const title = ref('')
const list = ref([])
const total = ref(0)
const ids = ref([])
const dateRange = ref([])
const formRef = ref()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  configName: '',
  configKey: '',
  configType: ''
})

const defaultForm = () => ({
  configId: null,
  configName: '',
  configKey: '',
  configValue: '',
  configType: 'N',
  remark: ''
})
const form = ref(defaultForm())

const rules = {
  configName: [{ required: true, message: '参数名称不能为空', trigger: 'blur' }],
  configKey: [{ required: true, message: '参数键名不能为空', trigger: 'blur' }],
  configValue: [{ required: true, message: '参数键值不能为空', trigger: 'blur' }]
}

async function getList() {
  loading.value = true
  try {
    const res = await listConfig(addDateRange(queryParams, dateRange.value))
    list.value = res?.rows || []
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
  queryParams.configName = ''
  queryParams.configKey = ''
  queryParams.configType = ''
  queryParams.pageNum = 1
  dateRange.value = []
  getList()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.configId)
}

function handleAdd() {
  form.value = defaultForm()
  title.value = '新增参数'
  open.value = true
}

async function handleUpdate(row) {
  let detail = { ...row }
  try {
    const res = await getConfig(row.configId)
    if (res) detail = { ...row, ...res }
  } catch (e) {
    /* 使用行数据 */
  }
  form.value = { ...defaultForm(), ...detail }
  title.value = '修改参数'
  open.value = true
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.value.configId != null) {
        await updateConfig(form.value)
        ElMessage.success('修改成功')
      } else {
        await addConfig(form.value)
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
  const configIds = row?.configId ? String(row.configId) : ids.value.join(',')
  if (!configIds) return
  try {
    await ElMessageBox.confirm(
      row?.configId ? `是否确认删除参数「${row.configName}」？` : `是否确认删除选中的 ${ids.value.length} 条数据？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delConfig(configIds)
  ElMessage.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
