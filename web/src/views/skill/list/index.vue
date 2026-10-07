<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 搜索栏 -->
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="名称/编码">
          <el-input v-model="queryParams.skillName" placeholder="技能名称或编码" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="全部分类" clearable style="width: 130px">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="queryParams.source" placeholder="全部来源" clearable style="width: 130px">
            <el-option label="官方内置" value="OFFICIAL" />
            <el-option label="导入" value="IMPORT" />
            <el-option label="自建" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 工具条 -->
      <div class="table-toolbar">
        <div>
          <el-button type="primary" icon="Plus" @click="handleAdd">新建技能</el-button>
          <el-button type="warning" icon="Upload" @click="openImport">上传 / 导入技能</el-button>
          <el-button icon="Goods" @click="marketOpen = true">内置技能市场</el-button>
          <el-button
            type="danger"
            icon="Delete"
            :disabled="ids.length === 0"
            @click="handleDelete()"
          >
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
      <el-table v-loading="loading" :data="list" row-key="skillId" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="技能" min-width="220">
          <template #default="{ row }">
            <div class="skill-cell">
              <span class="skill-icon">{{ row.icon || '🧩' }}</span>
              <div class="skill-meta">
                <div class="skill-name">{{ row.skillName }}</div>
                <div class="skill-code">{{ row.skillCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" prop="category" width="90" align="center">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceType(row.source)">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="简介" prop="summary" min-width="220" show-overflow-tooltip />
        <el-table-column label="被引用" width="130" align="center">
          <template #default="{ row }">
            <span v-if="(row.agentCount || 0) + (row.workflowCount || 0) === 0" class="ref-none">未使用</span>
            <span v-else>
              智能体 {{ row.agentCount || 0 }} · 流程 {{ row.workflowCount || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 0"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="handlePreview(row)">查看</el-button>
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button
              link
              type="danger"
              icon="Delete"
              :disabled="row.source === 'OFFICIAL'"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无技能，可点击「新建技能」「导入技能」或从「内置技能市场」添加</div>
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="open" :title="title" width="920px" append-to-body destroy-on-close>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="base">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="技能名称" prop="skillName">
              <el-input v-model="form.skillName" placeholder="如：周报撰写助手" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="技能编码" prop="skillCode">
              <el-input v-model="form.skillCode" placeholder="英文唯一标识，如 weekly-report" :disabled="form.skillId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="图标">
              <el-input v-model="form.icon" placeholder="emoji" maxlength="8" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分类">
              <el-select v-model="form.category" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="1.0.0" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="一句话简介">
              <el-input v-model="form.summary" placeholder="用一句话说明这个技能做什么" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标签">
              <el-select v-model="form.tagList" multiple filterable allow-create placeholder="回车创建标签" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="技能内容" prop="content">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="14"
                placeholder="用 Markdown 写清楚：角色、适用场景、输入输出规范、约束条件。这段内容会注入到大模型的系统提示词中。"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="技能内容即提示词"
          description="保存后可在【智能体管理】编辑智能体时挂载该技能，也可在工作流设计器中为流程挂载。挂载后调用大模型时会自动注入这段内容。"
        />
      </el-form>
        </el-tab-pane>

        <!-- 脚本页签：脚本以文件形式放在技能目录 scripts/ 下，对话中可被模型自动调用 -->
        <el-tab-pane name="script">
          <template #label>
            脚本<span v-if="scripts.length" class="tab-badge">{{ scripts.length }}</span>
          </template>

          <el-empty v-if="!form.skillId" description="技能保存后即可添加脚本" :image-size="70" />
          <template v-else>
            <div class="script-toolbar">
              <el-button type="primary" icon="Plus" size="small" @click="openScript()">新建脚本文件</el-button>
              <el-button icon="Refresh" size="small" @click="handleSyncScripts">重新扫描目录</el-button>
              <el-button icon="Upload" size="small" @click="pickScriptFiles">上传脚本文件</el-button>
              <span class="script-tip">
                脚本是技能目录里的 .py 文件（默认 scripts/ 下），编码会被注入提示词，对话时模型可自主调用；
                下划线开头的 .py 视为内部模块，不注册为工具
              </span>
            </div>
            <el-table :data="scripts" size="small" border>
              <el-table-column prop="scriptCode" label="编码" width="140" />
              <el-table-column prop="scriptName" label="名称" min-width="110" />
              <el-table-column prop="scriptPath" label="文件" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="file-path">{{ row.scriptPath || '（未关联文件）' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="用途说明" min-width="160" show-overflow-tooltip />
              <el-table-column prop="timeoutSec" label="超时(秒)" width="86" align="center" />
              <el-table-column label="状态" width="76" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="Number(row.status) === 0 ? 'success' : 'info'">
                    {{ Number(row.status) === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="文件" width="76" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.fileExists === false ? 'danger' : 'success'">
                    {{ row.fileExists === false ? '缺失' : '存在' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openScript(row)">编辑</el-button>
                  <el-button link type="success" @click="openScript(row, true)">运行</el-button>
                  <el-button link type="danger" @click="removeScript(row)">删除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <div class="empty-tip">
                  该技能还没有脚本，可「新建脚本文件」或「重新扫描目录」把已上传的 .py 注册进来
                </div>
              </template>
            </el-table>
          </template>
        </el-tab-pane>

        <!-- 文件页签：技能目录里的全部文件 -->
        <el-tab-pane name="file">
          <template #label>
            文件<span v-if="fileTree.length" class="tab-badge">{{ fileTree.length }}</span>
          </template>

          <el-empty v-if="!form.skillId" description="技能保存后即可管理目录文件" :image-size="70" />
          <template v-else>
            <div class="script-toolbar">
              <el-button type="primary" icon="Upload" size="small" @click="pickDirFiles">上传文件/文件夹</el-button>
              <el-button icon="Plus" size="small" @click="newFile">新建文件</el-button>
              <el-button icon="Refresh" size="small" @click="loadFiles">刷新</el-button>
              <el-button icon="Download" size="small" @click="exportDir">导出 ZIP</el-button>
              <span class="script-tip" :title="skillRootPath">目录：{{ form.skillCode || '-' }}/</span>
            </div>
            <div class="file-panel">
              <div class="file-tree">
                <el-tree
                  :data="fileTree"
                  :props="{ label: 'name', children: 'children' }"
                  node-key="path"
                  highlight-current
                  default-expand-all
                  @node-click="onFileClick"
                >
                  <template #default="{ data }">
                    <span class="tree-node">
                      <span class="tree-icon">{{ data.dir ? '📁' : fileIcon(data.name) }}</span>
                      <span>{{ data.name }}</span>
                      <el-button
                        v-if="!data.dir"
                        link
                        type="danger"
                        size="small"
                        class="tree-del"
                        @click.stop="removeFile(data)"
                      >
                        删除
                      </el-button>
                    </span>
                  </template>
                </el-tree>
              </div>
              <div class="file-editor">
                <template v-if="currentFile">
                  <div class="file-editor__head">
                    <span>{{ currentFile }}</span>
                    <el-button type="primary" size="small" :loading="fileSaving" @click="saveFile">
                      保存文件
                    </el-button>
                  </div>
                  <el-input
                    v-model="fileContent"
                    type="textarea"
                    :rows="18"
                    class="code-area"
                    placeholder="文件内容"
                  />
                </template>
                <el-empty v-else description="左侧选择文件后在这里编辑" :image-size="70" />
              </div>
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 隐藏的文件选择器：目录上传用 webkitdirectory，脚本上传用普通多选 -->
      <input
        ref="dirInputRef"
        type="file"
        webkitdirectory
        directory
        multiple
        style="display: none"
        @change="onDirPicked"
      />
      <input
        ref="fileInputRef"
        type="file"
        multiple
        accept=".py,.md,.txt,.json,.csv,.tsv,.yaml,.yml,.sql,.ini,.toml"
        style="display: none"
        @change="onScriptFilesPicked"
      />

      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 脚本编辑 / 运行弹窗 -->
    <el-dialog v-model="scriptOpen" :title="scriptTitle" width="880px" append-to-body destroy-on-close>
      <el-form :model="scriptForm" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="脚本名称">
              <el-input v-model="scriptForm.scriptName" placeholder="如：SQL 成本估算" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="脚本编码">
              <el-input
                v-model="scriptForm.scriptCode"
                :disabled="!!scriptForm.scriptId"
                placeholder="英文标识，如 explain_cost"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用途说明">
          <el-input
            v-model="scriptForm.description"
            placeholder="给大模型看：什么时候该调用这个脚本"
          />
        </el-form-item>
        <el-form-item label="参数说明">
          <el-input
            v-model="scriptForm.paramDesc"
            type="textarea"
            :rows="2"
            placeholder='JSON 样例，如 {"table_rows": 100000, "selectivity": 0.01}'
          />
        </el-form-item>
        <el-form-item label="文件路径">
          <el-input
            v-model="scriptForm.scriptPath"
            placeholder="技能目录下的相对路径，如 scripts/explain_cost.py"
          />
        </el-form-item>
        <el-form-item label="超时(秒)">
          <el-input-number v-model="scriptForm.timeoutSec" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="脚本内容">
          <el-input
            v-model="scriptForm.content"
            type="textarea"
            :rows="14"
            class="code-area"
            placeholder="在这里写 Python 代码，保存后会写入技能目录下对应的 .py 文件"
          />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="脚本以文件形式存放，执行工作目录就是技能目录"
        description="入参通过环境变量 SKILL_PARAMS（JSON 字符串）或 SKILL_INPUT_FILE 指向的 input.json 传入；脚本可直接相对路径读取目录里的资源文件，也能 import 同目录的其它 .py 模块；print 到 stdout 的内容作为执行结果返回。"
        style="margin-bottom: 12px"
      />

      <el-divider content-position="left">运行测试</el-divider>
      <el-input
        v-model="scriptForm.testParams"
        type="textarea"
        :rows="3"
        class="code-area"
        placeholder='入参 JSON，如 {"table_rows": 100000, "selectivity": 0.01, "has_index": true}'
      />
      <div class="run-bar">
        <el-button type="success" icon="VideoPlay" :loading="runLoading" @click="runTest">
          运行脚本
        </el-button>
        <span v-if="runResult" class="run-stat">
          <el-tag size="small" :type="runResult.success ? 'success' : 'danger'">
            {{ runResult.success ? '成功' : '失败' }}
          </el-tag>
          退出码 {{ runResult.exitCode }} · 耗时 {{ runResult.costMs }}ms
          <el-tag v-if="runResult.timedOut" size="small" type="warning">超时终止</el-tag>
        </span>
      </div>
      <div v-if="runResult" class="run-result">
        <div v-if="runResult.stdout">
          <div class="res-label">标准输出</div>
          <pre>{{ runResult.stdout }}</pre>
        </div>
        <div v-if="runResult.stderr">
          <div class="res-label">错误输出</div>
          <pre class="err">{{ runResult.stderr }}</pre>
        </div>
        <div v-if="runResult.errorMsg">
          <div class="res-label">说明</div>
          <pre class="err">{{ runResult.errorMsg }}</pre>
        </div>
      </div>

      <template #footer>
        <el-button @click="scriptOpen = false">取 消</el-button>
        <el-button type="primary" :loading="scriptLoading" @click="submitScript">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 上传 / 导入弹窗 -->
    <el-dialog v-model="importOpen" title="上传 / 导入技能" width="760px" append-to-body destroy-on-close>
      <el-tabs v-model="importTab">
        <!-- 方式一：上传整个技能文件夹 -->
        <el-tab-pane label="上传文件夹" name="folder">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="选择你已经写好的技能目录"
            description="目录里应包含 SKILL.md 与 scripts/（里面放 Python 脚本）。选择文件夹后系统按原目录结构整包上传，scripts 下的 .py 会自动注册为可调用工具。"
            style="margin-bottom: 14px"
          />
          <div class="upload-zone" @click="pickFolder">
            <el-icon class="upload-icon"><FolderOpened /></el-icon>
            <div class="upload-text">点击选择技能文件夹</div>
            <div class="upload-sub">支持子目录，保留原有目录结构</div>
          </div>
          <input
            ref="folderInput"
            type="file"
            webkitdirectory
            directory
            multiple
            style="display: none"
            @change="onFolderPicked"
          />
          <div v-if="folderFiles.length" class="file-list">
            <div class="file-list__head">
              已选择 {{ folderFiles.length }} 个文件
              <el-button link type="danger" @click="folderFiles = []">清空</el-button>
            </div>
            <ul>
              <li v-for="f in folderFiles.slice(0, 12)" :key="f.path">{{ f.path }}</li>
              <li v-if="folderFiles.length > 12" class="file-more">
                …等共 {{ folderFiles.length }} 个文件
              </li>
            </ul>
          </div>
        </el-tab-pane>

        <!-- 方式二：上传 ZIP 包 -->
        <el-tab-pane label="上传 ZIP 包" name="zip">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="把技能目录打包成 ZIP 上传"
            description="ZIP 内可以是技能目录本身，也可以是直接包含 SKILL.md 与 scripts/ 的文件列表。"
            style="margin-bottom: 14px"
          />
          <div class="upload-zone" @click="pickZip">
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-text">点击选择 ZIP 技能包</div>
            <div class="upload-sub">单个压缩包不超过 20MB</div>
          </div>
          <input
            ref="zipInput"
            type="file"
            accept=".zip"
            style="display: none"
            @change="onZipPicked"
          />
          <div v-if="zipFile" class="file-list">
            <div class="file-list__head">
              已选择：{{ zipFile.name }}（{{ Math.ceil(zipFile.size / 1024) }} KB）
            </div>
            <div v-if="zipPreview" class="zip-preview">
              <div><b>技能名：</b>{{ zipPreview.name || zipPreview.skillName || '（未解析到）' }}</div>
              <div v-if="zipPreview.description"><b>简介：</b>{{ zipPreview.description }}</div>
              <div><b>脚本：</b>{{ (zipPreview.scripts || []).join('、') || '（无）' }}</div>
              <div><b>文件数：</b>{{ zipPreview.fileCount }}</div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 方式三：粘贴 SKILL.md -->
        <el-tab-pane label="粘贴内容" name="text">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="支持粘贴 SKILL.md 全文"
            description="若内容含 YAML frontmatter（--- 包裹的 name / description），系统会自动解析出名称与简介。此方式只导入说明，不带脚本文件。"
            style="margin-bottom: 14px"
          />
          <el-form :model="importForm" label-width="86px">
            <el-form-item label="技能内容" required>
              <el-input
                v-model="importForm.content"
                type="textarea"
                :rows="12"
                placeholder="把别人发布的 SKILL.md 内容整段粘贴到这里"
              />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="importForm.skillName" placeholder="留空则自动从 frontmatter 解析" />
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="importForm.category" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <el-form label-width="86px" style="margin-top: 14px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="技能编码">
              <el-input v-model="uploadCode" placeholder="留空则按目录名 / frontmatter 自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="同名处理">
              <el-switch v-model="uploadOverwrite" active-text="覆盖已有" inactive-text="另建副本" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="importOpen = false">取 消</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">导 入</el-button>
      </template>
    </el-dialog>

    <!-- 内置技能市场 -->
    <el-dialog v-model="marketOpen" title="内置技能市场" width="820px" append-to-body destroy-on-close>
      <div class="market-grid">
        <div v-for="s in officialSkills" :key="s.skillId" class="market-card">
          <div class="market-head">
            <span class="market-icon">{{ s.icon }}</span>
            <div>
              <div class="market-name">{{ s.skillName }}</div>
              <el-tag size="small" type="primary">{{ s.category }}</el-tag>
            </div>
          </div>
          <p class="market-summary">{{ s.summary }}</p>
          <div class="market-foot">
            <el-button link type="primary" @click="handlePreview(s)">查看内容</el-button>
            <el-button link type="success" @click="cloneAsCustom(s)">复制为自建技能</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="marketOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 内容预览抽屉 -->
    <el-drawer v-model="previewOpen" :title="preview.skillName || '技能内容'" size="52%">
      <div class="preview-head">
        <span class="preview-icon">{{ preview.icon || '🧩' }}</span>
        <div>
          <div class="preview-name">{{ preview.skillName }}</div>
          <div class="preview-sub">
            {{ preview.skillCode }} · {{ sourceLabel(preview.source) }} · v{{ preview.version || '1.0.0' }}
          </div>
        </div>
      </div>
      <el-divider />
      <div class="md-body" v-html="renderMd(previewContent)"></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, UploadFilled } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import {
  listSkills,
  addSkill,
  editSkill,
  delSkill,
  changeSkillStatus,
  skillCategories,
  importSkill,
  listSkillScripts,
  addSkillScript,
  editSkillScript,
  delSkillScript,
  runSkillScript,
  runInlineScript,
  readScriptContent,
  saveScriptContent,
  syncSkillScripts,
  skillRoot,
  skillFiles,
  readSkillFile,
  writeSkillFile,
  deleteSkillFile,
  uploadSkillFiles,
  reloadSkill,
  exportSkill,
  importSkillFolder,
  importSkillZip,
  previewSkillZip
} from '@/api/skill'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' + hljs.highlight(code, { language: lang, ignoreIllegals: true }).value + '</code></pre>'
      } catch (e) {
        /* ignore */
      }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(code) + '</code></pre>'
  }
})
const renderMd = (t) => (t ? md.render(t) : '')

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const list = ref([])
const total = ref(0)
const ids = ref([])
const open = ref(false)
const importOpen = ref(false)
const marketOpen = ref(false)
const previewOpen = ref(false)
const title = ref('')
const categories = ref([])
const formRef = ref()

const preview = ref({})
const previewContent = computed(() => preview.value.content || '')

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  skillName: '',
  category: undefined,
  source: undefined,
  status: undefined
})

const defaultForm = () => ({
  skillId: null,
  skillName: '',
  skillCode: '',
  category: undefined,
  icon: '🧩',
  version: '1.0.0',
  summary: '',
  content: '',
  tagList: []
})
const form = ref(defaultForm())

const importForm = reactive({
  content: '',
  skillName: '',
  category: undefined
})

/* ------------------------------ 技能脚本 ------------------------------ */
/** 编辑弹窗当前页签 */
const activeTab = ref('base')
const scripts = ref([])
const scriptOpen = ref(false)
const scriptTitle = ref('')
const scriptLoading = ref(false)
const runLoading = ref(false)
const runResult = ref(null)
/** 打开脚本时的原始内容，用于判断运行时该调「已保存脚本」还是「调试执行」 */
let originContent = ''

const defaultScriptForm = () => ({
  scriptId: null,
  skillId: null,
  scriptName: '',
  scriptCode: '',
  description: '',
  paramDesc: '',
  language: 'python',
  content: '',
  timeoutSec: 30,
  testParams: '{}'
})
const scriptForm = ref(defaultScriptForm())

async function loadScripts() {
  if (!form.value.skillId) {
    scripts.value = []
    return
  }
  try {
    scripts.value = (await listSkillScripts(form.value.skillId)) || []
  } catch (e) {
    scripts.value = []
  }
}

/** 重新扫描技能目录：目录里新增的 .py 自动注册为工具 */
async function handleSyncScripts() {
  if (!form.value.skillId) return
  try {
    const res = await syncSkillScripts(form.value.skillId)
    const added = res?.added || []
    const migrated = res?.migrated || []
    const missing = res?.missing || []
    if (!added.length && !migrated.length && !missing.length) {
      ElMessage.info(`扫描到 ${res?.scanned || 0} 个 .py 文件，没有变化`)
    } else {
      ElMessage.success(
        `新增 ${added.length} 个${added.length ? '（' + added.join('、') + '）' : ''}` +
          (migrated.length ? `，迁移 ${migrated.length} 个历史脚本为文件` : '') +
          (missing.length ? `，${missing.length} 个脚本文件缺失` : '')
      )
    }
    await loadScripts()
    await loadFiles()
  } catch (e) {
    /* 统一提示 */
  }
}

/** 打开脚本编辑弹窗；runOnly=true 时读取文件后直接运行 */
async function openScript(row, runOnly = false) {
  runResult.value = null
  scriptForm.value = {
    ...defaultScriptForm(),
    ...(row || {}),
    skillId: form.value.skillId,
    timeoutSec: Number(row?.timeoutSec) || 30
  }
  // 脚本源码存在技能目录的文件里，打开时从文件读回来
  if (row?.scriptId) {
    try {
      const res = await readScriptContent(row.scriptId)
      scriptForm.value.content = res?.content || ''
      scriptForm.value.scriptPath = res?.scriptPath || row.scriptPath || ''
    } catch (e) {
      scriptForm.value.content = row.content || ''
    }
  } else {
    scriptForm.value.scriptPath = row?.scriptPath || `scripts/${scriptForm.value.scriptCode || 'main'}.py`
  }
  originContent = scriptForm.value.content || ''
  scriptTitle.value = row?.scriptId
    ? `编辑脚本${scriptForm.value.scriptPath ? '（' + scriptForm.value.scriptPath + '）' : ''}`
    : '新建脚本文件'
  scriptOpen.value = true
  if (runOnly) {
    await nextTick()
    await runTest()
  }
}

async function submitScript() {
  if (!scriptForm.value.scriptName || !scriptForm.value.scriptCode) {
    ElMessage.warning('脚本名称与编码不能为空')
    return
  }
  if (!scriptForm.value.content || !scriptForm.value.content.trim()) {
    ElMessage.warning('脚本内容不能为空')
    return
  }
  scriptLoading.value = true
  try {
    const payload = { ...scriptForm.value, skillId: form.value.skillId }
    delete payload.testParams
    if (scriptForm.value.scriptId) {
      await editSkillScript(payload)
      // 内容有改动时单独写回技能目录下的 .py 文件
      if (scriptForm.value.content !== originContent) {
        await saveScriptContent(scriptForm.value.scriptId, { content: scriptForm.value.content })
      }
      ElMessage.success('脚本修改成功')
    } else {
      await addSkillScript(payload)
      ElMessage.success('脚本文件创建成功')
    }
    originContent = scriptForm.value.content
    scriptOpen.value = false
    await loadScripts()
    await loadFiles()
  } catch (e) {
    /* 统一提示 */
  } finally {
    scriptLoading.value = false
  }
}

async function removeScript(row) {
  try {
    await ElMessageBox.confirm(`确认删除脚本「${row.scriptName}」？`, '警告', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await delSkillScript(row.scriptId)
  ElMessage.success('删除成功')
  await loadScripts()
}

/**
 * 运行测试：脚本已保存时先确保文件内容与编辑器一致，再执行技能目录里的脚本文件；
 * 未保存的新脚本走调试执行。
 */
async function runTest() {
  if (!scriptForm.value.content || !scriptForm.value.content.trim()) {
    ElMessage.warning('请先填写脚本内容')
    return
  }
  let params = scriptForm.value.testParams
  if (!params || !params.trim()) {
    params = '{}'
  }
  runLoading.value = true
  runResult.value = null
  try {
    if (scriptForm.value.scriptId) {
      if (scriptForm.value.content !== originContent) {
        await saveScriptContent(scriptForm.value.scriptId, { content: scriptForm.value.content })
        originContent = scriptForm.value.content
      }
      runResult.value = await runSkillScript({ scriptId: scriptForm.value.scriptId, params })
    } else {
      runResult.value = await runInlineScript({
        code: scriptForm.value.content,
        params,
        timeoutSec: scriptForm.value.timeoutSec,
        skillId: form.value.skillId
      })
    }
  } catch (e) {
    /* 统一提示 */
  } finally {
    runLoading.value = false
  }
}

/** 打开技能弹窗后同步加载脚本与文件 */
async function afterOpenSkill() {
  activeTab.value = 'base'
  runResult.value = null
  fileTree.value = []
  currentFile.value = ''
  fileContent.value = ''
  await Promise.all([loadScripts(), loadFiles()])
}

/* ------------------------------ 技能目录文件 ------------------------------ */
const fileTree = ref([])
const currentFile = ref('')
const fileContent = ref('')
const fileSaving = ref(false)
const dirInputRef = ref()
const fileInputRef = ref()

async function loadFiles() {
  if (!form.value.skillId) {
    fileTree.value = []
    return
  }
  try {
    fileTree.value = (await skillFiles(form.value.skillId)) || []
  } catch (e) {
    fileTree.value = []
  }
}

const fileIcon = (name) => {
  const n = (name || '').toLowerCase()
  if (n.endsWith('.py')) return '🐍'
  if (n.endsWith('.md')) return '📝'
  if (n.endsWith('.json')) return '🧾'
  if (n.endsWith('.csv') || n.endsWith('.tsv')) return '📊'
  if (n.endsWith('.yaml') || n.endsWith('.yml')) return '⚙️'
  return '📄'
}

async function onFileClick(data) {
  if (!data || data.dir) return
  currentFile.value = data.path
  try {
    const res = await readSkillFile(form.value.skillId, data.path)
    fileContent.value = res?.content || ''
  } catch (e) {
    fileContent.value = ''
  }
}

async function saveFile() {
  if (!currentFile.value) return
  fileSaving.value = true
  try {
    await writeSkillFile(form.value.skillId, { path: currentFile.value, content: fileContent.value })
    ElMessage.success('文件已保存')
  } catch (e) {
    /* 统一提示 */
  } finally {
    fileSaving.value = false
  }
}

async function newFile() {
  try {
    const { value } = await ElMessageBox.prompt('文件路径（相对技能目录）', '新建文件', {
      inputPlaceholder: 'scripts/my_tool.py 或 references/guide.md',
      confirmButtonText: '创建',
      cancelButtonText: '取消'
    })
    const path = (value || '').trim()
    if (!path) return
    await writeSkillFile(form.value.skillId, { path, content: '' })
    ElMessage.success('文件已创建')
    await loadFiles()
    if (path.endsWith('.py')) {
      await handleSyncScripts()
    }
  } catch (e) {
    /* 取消或失败 */
  }
}

async function removeFile(data) {
  try {
    await ElMessageBox.confirm(`确认删除文件「${data.path}」？`, '警告', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await deleteSkillFile(form.value.skillId, data.path)
  if (currentFile.value === data.path) {
    currentFile.value = ''
    fileContent.value = ''
  }
  ElMessage.success('删除成功')
  await loadFiles()
  await loadScripts()
}

/** 上传文件到技能目录（文件夹或单文件） */
function pickDirFiles() {
  dirInputRef.value?.click()
}
function pickScriptFiles() {
  fileInputRef.value?.click()
}

/** 把选中的文件按相对路径提交到技能目录 */
async function submitUploadedFiles(fileList, useWebkitPath) {
  const files = Array.from(fileList || [])
  if (!files.length) return
  const fd = new FormData()
  const paths = []
  files.forEach((f) => {
    const rel = useWebkitPath ? f.webkitRelativePath || f.name : f.name
    paths.push(rel)
    fd.append('files', f)
  })
  fd.append('paths', JSON.stringify(paths))
  const res = await uploadSkillFiles(form.value.skillId, fd)
  ElMessage.success(`已上传 ${res?.count ?? files.length} 个文件`)
  await loadFiles()
  await loadScripts()
  await handleSyncScripts()
}

async function onDirPicked(e) {
  const files = e.target.files
  try {
    await submitUploadedFiles(files, true)
  } catch (err) {
    /* 统一提示 */
  } finally {
    e.target.value = ''
  }
}

async function onScriptFilesPicked(e) {
  const files = e.target.files
  try {
    await submitUploadedFiles(files, true)
  } catch (err) {
    /* 统一提示 */
  } finally {
    e.target.value = ''
  }
}

/** 导出技能目录 ZIP */
async function exportDir() {
  try {
    const res = await exportSkill(form.value.skillId)
    const blob = res?.data instanceof Blob ? res.data : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${form.value.skillCode || 'skill'}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    /* 统一提示 */
  }
}

/* ------------------------------ 上传 / 导入技能包 ------------------------------ */
/** 服务端技能文件根目录，鼠标悬停可见 */
const skillRootPath = ref('')
const importTab = ref('folder')
const folderFiles = ref([])
const zipFile = ref(null)
const zipPreview = ref(null)
const uploadCode = ref('')
const uploadOverwrite = ref(false)
const folderInput = ref()
const zipInput = ref()

function openImport() {
  importTab.value = 'folder'
  folderFiles.value = []
  zipFile.value = null
  zipPreview.value = null
  uploadCode.value = ''
  uploadOverwrite.value = false
  importOpen.value = true
}

function pickFolder() {
  folderInput.value?.click()
}
function pickZip() {
  zipInput.value?.click()
}

function onFolderPicked(e) {
  const files = Array.from(e.target.files || [])
  folderFiles.value = files.map((f) => ({ path: f.webkitRelativePath || f.name, file: f }))
}

async function onZipPicked(e) {
  const file = (e.target.files || [])[0]
  if (!file) return
  zipFile.value = file
  zipPreview.value = null
  const fd = new FormData()
  fd.append('file', file)
  try {
    zipPreview.value = await previewSkillZip(fd)
  } catch (err) {
    /* 预览失败不阻塞导入 */
  }
}

const rules = {
  skillName: [{ required: true, message: '技能名称不能为空', trigger: 'blur' }],
  skillCode: [{ required: true, message: '技能编码不能为空', trigger: 'blur' }],
  content: [{ required: true, message: '技能内容不能为空', trigger: 'blur' }]
}

/** 内置（官方）技能，用于「技能市场」展示 */
const officialSkills = computed(() => list.value.filter((s) => s.source === 'OFFICIAL'))

const sourceLabel = (s) => ({ OFFICIAL: '官方内置', IMPORT: '导入', CUSTOM: '自建' }[s] || '自建')
const sourceType = (s) => ({ OFFICIAL: 'primary', IMPORT: 'warning', CUSTOM: 'success' }[s] || 'info')

async function getList() {
  loading.value = true
  try {
    const res = await listSkills(queryParams)
    list.value = (res?.rows || []).map((s) => ({ ...s, status: Number(s.status) }))
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
  queryParams.skillName = ''
  queryParams.category = undefined
  queryParams.source = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}
function handleSelectionChange(selection) {
  ids.value = selection.map((i) => i.skillId)
}

function handleAdd() {
  form.value = defaultForm()
  title.value = '新建技能'
  open.value = true
  afterOpenSkill()
}

async function handleUpdate(row) {
  title.value = '修改技能'
  form.value = {
    ...defaultForm(),
    ...row,
    tagList: row.tagList || []
  }
  open.value = true
  await afterOpenSkill()
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.value.skillId) {
        await editSkill(form.value)
        ElMessage.success('修改成功')
      } else {
        await addSkill(form.value)
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

async function submitImport() {
  importLoading.value = true
  try {
    if (importTab.value === 'folder') {
      if (!folderFiles.value.length) {
        ElMessage.warning('请先选择技能文件夹')
        return
      }
      const fd = new FormData()
      const paths = []
      folderFiles.value.forEach((f) => {
        paths.push(f.path)
        fd.append('files', f.file)
      })
      fd.append('paths', JSON.stringify(paths))
      if (uploadCode.value.trim()) fd.append('skillCode', uploadCode.value.trim())
      fd.append('overwrite', String(uploadOverwrite.value))
      const skill = await importSkillFolder(fd)
      ElMessage.success(`技能「${skill?.skillName || skill?.skillCode}」已导入到 ${skill?.skillCode}/ 目录`)
    } else if (importTab.value === 'zip') {
      if (!zipFile.value) {
        ElMessage.warning('请先选择 ZIP 技能包')
        return
      }
      const fd = new FormData()
      fd.append('file', zipFile.value)
      if (uploadCode.value.trim()) fd.append('skillCode', uploadCode.value.trim())
      fd.append('overwrite', String(uploadOverwrite.value))
      const skill = await importSkillZip(fd)
      ElMessage.success(`技能「${skill?.skillName || skill?.skillCode}」已导入到 ${skill?.skillCode}/ 目录`)
    } else {
      if (!importForm.content || !importForm.content.trim()) {
        ElMessage.warning('请粘贴技能内容')
        return
      }
      await importSkill({
        content: importForm.content,
        skillName: importForm.skillName,
        category: importForm.category,
        source: 'IMPORT'
      })
      ElMessage.success('导入成功')
      importForm.content = ''
      importForm.skillName = ''
    }
    importOpen.value = false
    getList()
  } catch (e) {
    /* 统一提示 */
  } finally {
    importLoading.value = false
  }
}

/** 把官方技能复制成一份自建技能，便于二次编辑 */
function cloneAsCustom(s) {
  form.value = {
    ...defaultForm(),
    skillName: s.skillName + '（副本）',
    skillCode: s.skillCode + '-copy-' + Date.now().toString().slice(-5),
    category: s.category,
    icon: s.icon,
    version: s.version,
    summary: s.summary,
    content: s.content,
    tagList: []
  }
  title.value = '复制为自建技能'
  marketOpen.value = false
  open.value = true
  afterOpenSkill()
}

async function handleDelete(row) {
  const skillIds = row?.skillId ? String(row.skillId) : ids.value.join(',')
  if (!skillIds) return
  try {
    await ElMessageBox.confirm(
      row?.skillId ? `确认删除技能「${row.skillName}」？` : `确认删除选中的 ${ids.value.length} 个技能？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delSkill(skillIds)
  ElMessage.success('删除成功')
  getList()
}

async function handleStatusChange(row, val) {
  const next = val ? 0 : 1
  const text = val ? '启用' : '停用'
  const prev = row.status
  row.status = next
  try {
    await changeSkillStatus({ skillId: row.skillId, status: next })
    ElMessage.success(`${text}成功`)
  } catch (e) {
    row.status = prev
  }
}

function handlePreview(row) {
  preview.value = row || {}
  previewOpen.value = true
}

onMounted(async () => {
  try {
    categories.value = (await skillCategories()) || []
  } catch (e) {
    categories.value = ['办公', '研发', '运维', '数据', '写作', '其他'].map((v) => ({ label: v, value: v }))
  }
  try {
    skillRootPath.value = (await skillRoot())?.path || ''
  } catch (e) {
    skillRootPath.value = ''
  }
  getList()
})
</script>

<style scoped lang="scss">
.skill-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.skill-icon {
  font-size: 22px;
  line-height: 1;
}
.skill-name {
  font-weight: 600;
  color: #303133;
}
.skill-code {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.ref-none {
  color: #c0c4cc;
}
.market-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.market-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  background: #fafafa;
}
.market-head {
  display: flex;
  gap: 10px;
  align-items: center;
}
.market-icon {
  font-size: 26px;
}
.market-name {
  font-weight: 600;
  margin-bottom: 4px;
}
.market-summary {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  margin: 10px 0;
  min-height: 40px;
}
.market-foot {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
.preview-head {
  display: flex;
  gap: 12px;
  align-items: center;
}
.preview-icon {
  font-size: 30px;
}
.preview-name {
  font-size: 16px;
  font-weight: 600;
}
.preview-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* ---------- 脚本页签 ---------- */
.tab-badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 9px;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}
.script-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.script-tip {
  font-size: 12px;
  color: #909399;
}
.code-area :deep(textarea) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.65;
}
.run-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px 0;
}
.run-stat {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
}
.run-result pre {
  margin: 4px 0 10px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow: auto;
}
.run-result pre.err {
  background: #fef0f0;
  color: #c45656;
}
.res-label {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

/* ---------- 上传区 ---------- */
.upload-zone {
  border: 1px dashed #d0d7de;
  border-radius: 8px;
  padding: 26px 16px;
  text-align: center;
  cursor: pointer;
  background: #fafbfc;
  transition: all 0.2s;
}
.upload-zone:hover {
  border-color: #409eff;
  background: #f0f7ff;
}
.upload-icon {
  font-size: 30px;
  color: #909399;
}
.upload-text {
  margin-top: 8px;
  font-size: 14px;
  color: #303133;
}
.upload-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.file-list {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafafa;
}
.file-list__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #303133;
  margin-bottom: 6px;
}
.file-list ul {
  margin: 0;
  padding-left: 16px;
  max-height: 168px;
  overflow: auto;
}
.file-list li {
  font-size: 12px;
  color: #606266;
  line-height: 1.9;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}
.file-more {
  color: #909399 !important;
}
.zip-preview {
  font-size: 12.5px;
  color: #606266;
  line-height: 1.9;
}

/* ---------- 文件页签 ---------- */
.file-path {
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
  font-size: 12px;
  color: #606266;
}
.file-panel {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.file-tree {
  width: 260px;
  min-height: 320px;
  max-height: 440px;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px;
  background: #fafafa;
}
.file-editor {
  flex: 1;
  min-width: 0;
}
.file-editor__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #303133;
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.tree-icon {
  font-size: 13px;
}
.tree-del {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.15s;
}
.tree-node:hover .tree-del {
  opacity: 1;
}
</style>
