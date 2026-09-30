<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- 左：字典类型 -->
      <el-col :xs="24" :md="11">
        <div class="page-card">
          <div class="card-header">
            <span>字典类型</span>
            <el-button type="primary" icon="Plus" size="small" @click="handleAddType">新增</el-button>
          </div>
          <el-form :model="typeQuery" inline class="search-form" @submit.prevent>
            <el-form-item label="字典名称">
              <el-input v-model="typeQuery.dictName" placeholder="字典名称" clearable style="width: 140px" />
            </el-form-item>
            <el-form-item label="字典类型">
              <el-input v-model="typeQuery.dictType" placeholder="字典类型" clearable style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" size="small" @click="getTypeList">搜索</el-button>
              <el-button icon="Refresh" size="small" @click="resetTypeQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="typeLoading"
            :data="typeList"
            highlight-current-row
            @row-click="handleTypeClick"
            @selection-change="handleTypeSelectionChange"
          >
            <el-table-column type="selection" width="46" align="center" />
            <el-table-column label="字典编号" prop="dictId" width="86" align="center" />
            <el-table-column label="字典名称" prop="dictName" min-width="110" show-overflow-tooltip />
            <el-table-column label="字典类型" prop="dictType" min-width="130" show-overflow-tooltip />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
                  {{ row.status === '0' ? '正常' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link type="primary" icon="Edit" @click.stop="handleUpdateType(row)">修改</el-button>
                <el-button link type="danger" icon="Delete" @click.stop="handleDeleteType(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-tip">暂无字典类型</div>
            </template>
          </el-table>

          <div class="pagination-container">
            <el-pagination
              v-model:current-page="typeQuery.pageNum"
              v-model:page-size="typeQuery.pageSize"
              :total="typeTotal"
              layout="total, prev, pager, next"
              background
              small
              @current-change="getTypeList"
            />
          </div>
        </div>
      </el-col>

      <!-- 右：字典数据 -->
      <el-col :xs="24" :md="13">
        <div class="page-card">
          <div class="card-header">
            <span>
              字典数据
              <el-tag v-if="currentType" size="small" type="primary" style="margin-left: 8px">
                {{ currentType }}
              </el-tag>
              <span v-else class="text-muted" style="font-size: 12px; margin-left: 8px">
                请在左侧选择一个字典类型
              </span>
            </span>
            <el-button type="primary" icon="Plus" size="small" :disabled="!currentType" @click="handleAddData">
              新增
            </el-button>
          </div>

          <el-table v-loading="dataLoading" :data="dataList" @selection-change="handleDataSelectionChange">
            <el-table-column type="selection" width="46" align="center" />
            <el-table-column label="字典编码" prop="dictCode" width="90" align="center" />
            <el-table-column label="字典标签" prop="dictLabel" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag :type="tagType(row.listClass)" size="small">{{ row.dictLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="字典键值" prop="dictValue" min-width="110" show-overflow-tooltip />
            <el-table-column label="排序" prop="dictSort" width="70" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
                  {{ row.status === '0' ? '正常' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link type="primary" icon="Edit" @click="handleUpdateData(row)">修改</el-button>
                <el-button link type="danger" icon="Delete" @click="handleDeleteData(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-tip">{{ currentType ? '暂无字典数据' : '请先选择字典类型' }}</div>
            </template>
          </el-table>

          <div class="pagination-container">
            <el-pagination
              v-model:current-page="dataQuery.pageNum"
              v-model:page-size="dataQuery.pageSize"
              :total="dataTotal"
              layout="total, prev, pager, next"
              background
              small
              @current-change="getDataList"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 字典类型 新增/修改 -->
    <el-dialog v-model="typeOpen" :title="typeTitle" width="520px" append-to-body destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="typeForm.dictType" placeholder="请输入字典类型，如 sys_user_sex" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="typeOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitTypeForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 字典数据 新增/修改 -->
    <el-dialog v-model="dataOpen" :title="dataTitle" width="520px" append-to-body destroy-on-close>
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item label="字典类型">
          <el-input v-model="dataForm.dictType" disabled />
        </el-form-item>
        <el-form-item label="数据标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="请输入数据标签" />
        </el-form-item>
        <el-form-item label="数据键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="请输入数据键值" />
        </el-form-item>
        <el-form-item label="显示排序" prop="dictSort">
          <el-input-number v-model="dataForm.dictSort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="回显样式">
          <el-select v-model="dataForm.listClass" placeholder="请选择样式" clearable style="width: 100%">
            <el-option v-for="item in listClassOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dataForm.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dataOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitDataForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDictType,
  getDictType,
  addDictType,
  updateDictType,
  delDictType,
  listDictData,
  getDictData,
  addDictData,
  updateDictData,
  delDictData
} from '@/api/system'
import { clearDictCache } from '@/utils/dict'

/* ------------------------------ 字典类型 ------------------------------ */
const typeLoading = ref(false)
const typeList = ref([])
const typeTotal = ref(0)
const typeIds = ref([])
const typeOpen = ref(false)
const typeTitle = ref('')
const typeFormRef = ref()

const typeQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  dictName: '',
  dictType: '',
  status: ''
})

const defaultTypeForm = () => ({
  dictId: null,
  dictName: '',
  dictType: '',
  status: '0',
  remark: ''
})
const typeForm = ref(defaultTypeForm())

const typeRules = {
  dictName: [{ required: true, message: '字典名称不能为空', trigger: 'blur' }],
  dictType: [{ required: true, message: '字典类型不能为空', trigger: 'blur' }]
}

async function getTypeList() {
  typeLoading.value = true
  try {
    const res = await listDictType(typeQuery)
    typeList.value = res?.rows || []
    typeTotal.value = res?.total || 0
  } catch (e) {
    typeList.value = []
    typeTotal.value = 0
  } finally {
    typeLoading.value = false
  }
}

function resetTypeQuery() {
  typeQuery.dictName = ''
  typeQuery.dictType = ''
  typeQuery.pageNum = 1
  getTypeList()
}

function handleTypeSelectionChange(selection) {
  typeIds.value = selection.map((item) => item.dictId)
}

function handleTypeClick(row) {
  currentType.value = row.dictType
  dataQuery.pageNum = 1
  getDataList()
}

function handleAddType() {
  typeForm.value = defaultTypeForm()
  typeTitle.value = '新增字典类型'
  typeOpen.value = true
}

async function handleUpdateType(row) {
  let detail = { ...row }
  try {
    const res = await getDictType(row.dictId)
    if (res) detail = { ...row, ...res }
  } catch (e) {
    /* 使用行数据 */
  }
  typeForm.value = { ...defaultTypeForm(), ...detail }
  typeTitle.value = '修改字典类型'
  typeOpen.value = true
}

function submitTypeForm() {
  typeFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (typeForm.value.dictId != null) {
        await updateDictType(typeForm.value)
        ElMessage.success('修改成功')
      } else {
        await addDictType(typeForm.value)
        ElMessage.success('新增成功')
      }
      typeOpen.value = false
      getTypeList()
    } catch (e) {
      /* 统一提示 */
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDeleteType(row) {
  const dictIds = row?.dictId ? String(row.dictId) : typeIds.value.join(',')
  if (!dictIds) return
  try {
    await ElMessageBox.confirm(
      row?.dictId ? `是否确认删除字典「${row.dictName}」？` : `是否确认删除选中的 ${typeIds.value.length} 条数据？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delDictType(dictIds)
  ElMessage.success('删除成功')
  getTypeList()
}

/* ------------------------------ 字典数据 ------------------------------ */
const submitLoading = ref(false)
const dataLoading = ref(false)
const dataList = ref([])
const dataTotal = ref(0)
const dataIds = ref([])
const dataOpen = ref(false)
const dataTitle = ref('')
const dataFormRef = ref()
const currentType = ref('')

const dataQuery = reactive({
  pageNum: 1,
  pageSize: 10
})

const listClassOptions = [
  { value: 'default', label: '默认(default)' },
  { value: 'primary', label: '主要(primary)' },
  { value: 'success', label: '成功(success)' },
  { value: 'info', label: '信息(info)' },
  { value: 'warning', label: '警告(warning)' },
  { value: 'danger', label: '危险(danger)' }
]

const tagType = (listClass) => listClassOptions.find((i) => i.value === listClass)?.value || ''

const defaultDataForm = () => ({
  dictCode: null,
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 1,
  listClass: 'default',
  status: '0',
  remark: ''
})
const dataForm = ref(defaultDataForm())

const dataRules = {
  dictLabel: [{ required: true, message: '数据标签不能为空', trigger: 'blur' }],
  dictValue: [{ required: true, message: '数据键值不能为空', trigger: 'blur' }],
  dictSort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
}

async function getDataList() {
  if (!currentType.value) {
    dataList.value = []
    dataTotal.value = 0
    return
  }
  dataLoading.value = true
  try {
    const res = await listDictData({ ...dataQuery, dictType: currentType.value })
    dataList.value = res?.rows || []
    dataTotal.value = res?.total || 0
  } catch (e) {
    dataList.value = []
    dataTotal.value = 0
  } finally {
    dataLoading.value = false
  }
}

function handleDataSelectionChange(selection) {
  dataIds.value = selection.map((item) => item.dictCode)
}

function handleAddData() {
  dataForm.value = { ...defaultDataForm(), dictType: currentType.value }
  dataTitle.value = '新增字典数据'
  dataOpen.value = true
}

async function handleUpdateData(row) {
  let detail = { ...row }
  try {
    const res = await getDictData(row.dictCode)
    if (res) detail = { ...row, ...res }
  } catch (e) {
    /* 使用行数据 */
  }
  dataForm.value = { ...defaultDataForm(), ...detail }
  dataTitle.value = '修改字典数据'
  dataOpen.value = true
}

function submitDataForm() {
  dataFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (dataForm.value.dictCode != null) {
        await updateDictData(dataForm.value)
        ElMessage.success('修改成功')
      } else {
        await addDictData(dataForm.value)
        ElMessage.success('新增成功')
      }
      dataOpen.value = false
      clearDictCache(dataForm.value.dictType)
      getDataList()
    } catch (e) {
      /* 统一提示 */
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleDeleteData(row) {
  const dictCodes = row?.dictCode ? String(row.dictCode) : dataIds.value.join(',')
  if (!dictCodes) return
  try {
    await ElMessageBox.confirm(
      row?.dictCode ? `是否确认删除「${row.dictLabel}」？` : `是否确认删除选中的 ${dataIds.value.length} 条数据？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delDictData(dictCodes)
  ElMessage.success('删除成功')
  getDataList()
}

onMounted(() => {
  getTypeList()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
</style>
