package com.shauiqiu.eklose

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val EK_BASE_URL = "https://mapi.ekwing.com"
private const val HOMEWORK_LIST_PATH = "/student/Hw/getList"
private const val BASIC_HOMEWORK_LIST_PATH = "/student/Hw/getBasicList"
private const val STUDY_CENTER_PATH = "/student/Hw/getnewmainlist"
private const val BASIC_STUDY_CENTER_PATH = "/student/Hw/getbasicnewmainlist"
private const val HOMEWORK_ITEMS_PATH = "/student/Hw/getHwItems"
private const val BASIC_HOMEWORK_ITEMS_PATH = "/student/Hw/getBasicHwItems"
private const val SCORE_DETAIL_PATH = "/student/Hw/stuscoredetail"
private const val BASIC_SCORE_DETAIL_PATH = "/student/Hw/stubasicscoredetail"
private const val HW_DO_ITEM_PATH = "/student/Hw/hwdoitem"
private const val HW_ANSWER_PATH = "/student/Hw/getHwAns"
private const val HW_COUNT_PATH = "/student/Hw/gethwcnt"
private const val HW_HISTORY_SCORE_PATH = "/student/Hw/jshistoryitemScore"
private const val HW_RESULT_PATH = "/student/Hw/GetHwResult"
private const val TRAIN_ITEM_ANSWER_PATH = "/student/train/getitemans"
private const val TRAIN_JS_ITEM_ANSWER_PATH = "/student/train/getjsitemans"
private const val EXAM_HISTORY_PATH = "/student/exam/getstuexamlist"
private const val BASIC_EXAM_HISTORY_PATH = "/student/exam/getstubasicexamlist"
private const val EXAM_ITEM_PATH = "/student/exam/getstuexamitem"
private const val EXAM_SCORE_PATH = "/student/exam/getscoreinfo"
private const val BASIC_EXAM_SCORE_PATH = "/student/exam/getbasicscoreinfo"
private const val GET_MODEL_SCORE_PATH = "/student/exam/getmodelscoreinfo"

data class EkwingAnswerPaper(
    val key: String,
    val title: String,
    val summary: String,
)

data class EkwingAnswerSection(
    val title: String,
    val category: String,
    val originalText: String,
    val questions: List<EkwingAnswerQuestion>,
)

data class EkwingAnswerQuestion(
    val order: String,
    val question: String,
    val answer: String,
)

data class EkwingClassRankItem(
    val index: Int,
    val name: String,
    val score: String,
    val isSelf: Boolean,
    val className: String = "",
    val studentNo: String = "",
)

data class EkwingClassScoreReport(
    val userRank: Int?,
    val userScore: String?,
    val className: String?,
    val ranks: List<EkwingClassRankItem>,
)

enum class EkwingAnswerSource(
    val cacheKey: String,
) {
    Current("current"),
    History("history"),
    HomeworkCurrent("homework_current"),
    HomeworkHistory("homework_history");

    val isHomework: Boolean
        get() = this == HomeworkCurrent || this == HomeworkHistory

    val isHistory: Boolean
        get() = this == History || this == HomeworkHistory
}

enum class EkwingAnswerParseStatus {
    Pending,
    Loading,
    Ready,
    Failed,
}

data class EkwingAnswerParseState(
    val status: EkwingAnswerParseStatus,
    val errorMessage: String? = null,
)

data class EkwingAnswerSourceState(
    val papers: List<EkwingAnswerPaper> = emptyList(),
    val taskByPaperKey: Map<String, String> = emptyMap(),
    val sectionsByPaperKey: Map<String, List<EkwingAnswerSection>> = emptyMap(),
    val parseStateByPaperKey: Map<String, EkwingAnswerParseState> = emptyMap(),
    val classScoreReportByPaperKey: Map<String, EkwingClassScoreReport> = emptyMap(),
)

internal fun canOpenPaper(parseState: EkwingAnswerParseState?): Boolean {
    return parseState?.status == EkwingAnswerParseStatus.Ready
}

/** Failed/error cache entries are diagnostics, not successful standard answers. */
internal fun canReuseAnswerCache(state: EkwingAnswerSourceState, paperKey: String): Boolean {
    val sections = state.sectionsByPaperKey[paperKey] ?: return false
    return state.parseStateByPaperKey[paperKey]?.status != EkwingAnswerParseStatus.Failed &&
        sections.none { it.category == "error" }
}

object EkwingAnswerState {
    private val stateFlow = MutableStateFlow(
        emptySourceStates()
    )

    val states: StateFlow<Map<EkwingAnswerSource, EkwingAnswerSourceState>> = stateFlow.asStateFlow()

    var papers: List<EkwingAnswerPaper>
        get() = sourceState(EkwingAnswerSource.Current).papers
        set(value) {
            updateSource(EkwingAnswerSource.Current) { it.copy(papers = value) }
        }
    var taskByPaperKey: Map<String, String>
        get() = sourceState(EkwingAnswerSource.Current).taskByPaperKey
        set(value) {
            updateSource(EkwingAnswerSource.Current) { it.copy(taskByPaperKey = value) }
        }
    var sectionsByPaperKey: Map<String, List<EkwingAnswerSection>>
        get() = sourceState(EkwingAnswerSource.Current).sectionsByPaperKey
        set(value) {
            updateSource(EkwingAnswerSource.Current) { it.copy(sectionsByPaperKey = value) }
        }
    var classScoreReportByPaperKey: Map<String, EkwingClassScoreReport>
        get() = sourceState(EkwingAnswerSource.Current).classScoreReportByPaperKey
        set(value) {
            updateSource(EkwingAnswerSource.Current) { it.copy(classScoreReportByPaperKey = value) }
        }

    fun sourceState(source: EkwingAnswerSource): EkwingAnswerSourceState {
        return stateFlow.value[source] ?: EkwingAnswerSourceState()
    }

    fun setSourceState(source: EkwingAnswerSource, state: EkwingAnswerSourceState) {
        stateFlow.update { current -> current + (source to state) }
    }

    fun updateSource(
        source: EkwingAnswerSource,
        transform: (EkwingAnswerSourceState) -> EkwingAnswerSourceState,
    ) {
        stateFlow.update { current ->
            val oldState = current[source] ?: EkwingAnswerSourceState()
            current + (source to transform(oldState))
        }
    }

    fun clear() {
        stateFlow.value = emptySourceStates()
    }

    private fun emptySourceStates(): Map<EkwingAnswerSource, EkwingAnswerSourceState> {
        return EkwingAnswerSource.entries.associateWith { EkwingAnswerSourceState() }
    }
}

object EkwingAnswerCacheStore {
    private const val PREFS_NAME = "ekwing_answer_cache"

    fun restore(context: Context) {
        EkwingAnswerSource.entries.forEach { source ->
            EkwingAnswerState.setSourceState(source, loadSourceState(context, source))
        }
    }

    fun saveSourceState(
        context: Context,
        source: EkwingAnswerSource,
        state: EkwingAnswerSourceState,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(source.cacheKey, sourceStateToJson(state).toString())
            .apply()
    }

    fun loadSourceState(context: Context, source: EkwingAnswerSource): EkwingAnswerSourceState {
        val text = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(source.cacheKey, null)
            ?: return EkwingAnswerSourceState()
        return runCatching { sourceStateFromJson(JSONObject(text)) }
            .getOrDefault(EkwingAnswerSourceState())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        EkwingAnswerState.clear()
    }
}

object EkwingAnswerPrefetchManager {
    private const val CONCURRENCY = 3
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(CONCURRENCY)
    private val jobs = mutableMapOf<String, Job>()

    fun enqueueAll(
        context: Context,
        source: EkwingAnswerSource,
        papers: List<EkwingAnswerPaper>,
    ) {
        val appContext = context.applicationContext
        papers.forEach { paper -> enqueue(appContext, source, paper.key) }
    }

    fun enqueue(
        context: Context,
        source: EkwingAnswerSource,
        paperKey: String,
    ) {
        if (paperKey.isBlank()) return
        val jobKey = "${source.cacheKey}:$paperKey"
        synchronized(jobs) {
            val existing = jobs[jobKey]
            if (existing?.isActive == true) return
            val currentState = EkwingAnswerState.sourceState(source)
            if (canReuseAnswerCache(currentState, paperKey)) {
                EkwingAnswerState.updateSource(source) {
                    it.copy(parseStateByPaperKey = it.parseStateByPaperKey + (paperKey to EkwingAnswerParseState(EkwingAnswerParseStatus.Ready)))
                }
                EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
                return
            }
            EkwingAnswerState.updateSource(source) {
                it.copy(
                    sectionsByPaperKey = it.sectionsByPaperKey - paperKey,
                    parseStateByPaperKey = it.parseStateByPaperKey + (paperKey to EkwingAnswerParseState(EkwingAnswerParseStatus.Pending)),
                )
            }
            EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    semaphore.withPermit {
                        parseOne(context.applicationContext, source, paperKey)
                    }
                } finally {
                    val ownJob = coroutineContext.job
                    synchronized(jobs) {
                        if (jobs[jobKey] === ownJob) jobs.remove(jobKey)
                    }
                }
            }
            jobs[jobKey] = job
            job.start()
        }
    }

    fun cancelAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
    }

    private suspend fun parseOne(
        context: Context,
        source: EkwingAnswerSource,
        paperKey: String,
    ) {
        EkwingAnswerState.updateSource(source) {
            it.copy(parseStateByPaperKey = it.parseStateByPaperKey + (paperKey to EkwingAnswerParseState(EkwingAnswerParseStatus.Loading)))
        }
        EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))

        val sections = runCatching {
            EkwingTokenRefreshManager.withTokenRefresh(context) {
                EkwingAnswerReader(context).readAnswerSections(source, paperKey)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            val failed = EkwingAnswerAssembler.readFailureSection(error)
            EkwingAnswerState.updateSource(source) {
                it.copy(
                    sectionsByPaperKey = it.sectionsByPaperKey + (paperKey to listOf(failed)),
                    parseStateByPaperKey = it.parseStateByPaperKey + (
                        paperKey to EkwingAnswerParseState(
                            status = EkwingAnswerParseStatus.Failed,
                            errorMessage = error.message,
                        )
                        ),
                )
            }
            EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
            return
        }

        currentCoroutineContext().ensureActive()
        val parseState = if (sections.any { it.category == "error" }) {
            EkwingAnswerParseState(
                status = EkwingAnswerParseStatus.Failed,
                errorMessage = sections.firstOrNull()?.questions?.firstOrNull()?.answer,
            )
        } else {
            EkwingAnswerParseState(EkwingAnswerParseStatus.Ready)
        }
        EkwingAnswerState.updateSource(source) {
            it.copy(
                sectionsByPaperKey = it.sectionsByPaperKey + (paperKey to sections),
                parseStateByPaperKey = it.parseStateByPaperKey + (paperKey to parseState),
            )
        }
        EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
    }
}

internal fun sourceStateToJson(state: EkwingAnswerSourceState): JSONObject {
    return JSONObject().apply {
        put("papers", JSONArray().apply { state.papers.forEach { put(it.toJson()) } })
        put("taskByPaperKey", JSONObject().apply {
            state.taskByPaperKey.forEach { (key, value) -> put(key, value) }
        })
        put("sectionsByPaperKey", JSONObject().apply {
            state.sectionsByPaperKey.forEach { (key, value) ->
                put(key, JSONArray().apply { value.forEach { put(it.toJson()) } })
            }
        })
        put("parseStateByPaperKey", JSONObject().apply {
            state.parseStateByPaperKey.forEach { (key, value) -> put(key, value.toJson()) }
        })
        put("classScoreReportByPaperKey", JSONObject().apply {
            state.classScoreReportByPaperKey.forEach { (key, report) ->
                put(key, report.toJson())
            }
        })
    }
}

internal fun sourceStateFromJson(value: JSONObject): EkwingAnswerSourceState {
    val papers = value.optJSONArray("papers").jsonObjects().map { it.toAnswerPaper() }
    val taskByPaperKey = value.optJSONObject("taskByPaperKey").toStringMap()
    val sectionsByPaperKey = mutableMapOf<String, List<EkwingAnswerSection>>()
    value.optJSONObject("sectionsByPaperKey")?.let { root ->
        root.keys().forEach { key ->
            sectionsByPaperKey[key] = root.optJSONArray(key).jsonObjects().map { it.toAnswerSection() }
        }
    }
    val parseStateByPaperKey = mutableMapOf<String, EkwingAnswerParseState>()
    value.optJSONObject("parseStateByPaperKey")?.let { root ->
        root.keys().forEach { key ->
            root.optJSONObject(key)?.toParseState()?.let { parseStateByPaperKey[key] = it }
        }
    }
    val classScoreReportByPaperKey = mutableMapOf<String, EkwingClassScoreReport>()
    value.optJSONObject("classScoreReportByPaperKey")?.let { root ->
        root.keys().forEach { key ->
            root.optJSONObject(key)?.toClassScoreReport()?.let {
                classScoreReportByPaperKey[key] = it
            }
        }
    }
    val readyStates = sectionsByPaperKey.keys.associateWith { EkwingAnswerParseState(EkwingAnswerParseStatus.Ready) }
    return EkwingAnswerSourceState(
        papers = papers,
        taskByPaperKey = taskByPaperKey,
        sectionsByPaperKey = sectionsByPaperKey,
        parseStateByPaperKey = parseStateByPaperKey + readyStates,
        classScoreReportByPaperKey = classScoreReportByPaperKey,
    )
}

internal fun EkwingClassScoreReport.toJson(): JSONObject {
    return JSONObject().apply {
        userRank?.let { put("userRank", it) }
        userScore?.let { put("userScore", it) }
        className?.let { put("className", it) }
        put("ranks", JSONArray().apply { ranks.forEach { put(it.toJson()) } })
    }
}

internal fun JSONObject.toClassScoreReport(): EkwingClassScoreReport {
    val userRank = if (has("userRank") && !isNull("userRank")) optInt("userRank") else null
    val userScore = optString("userScore").takeIf { it.isNotBlank() }
    val className = optString("className").takeIf { it.isNotBlank() }
    val ranks = optJSONArray("ranks")?.jsonObjects()?.map { it.toClassRankItem() }.orEmpty()
    return EkwingClassScoreReport(
        userRank = userRank,
        userScore = userScore,
        className = className,
        ranks = ranks,
    )
}

internal fun EkwingClassRankItem.toJson(): JSONObject {
    return JSONObject().apply {
        put("index", index)
        put("name", name)
        put("score", score)
        put("isSelf", isSelf)
        put("className", className)
        put("studentNo", studentNo)
    }
}

internal fun JSONObject.toClassRankItem(): EkwingClassRankItem {
    return EkwingClassRankItem(
        index = optInt("index"),
        name = optString("name"),
        score = optString("score"),
        isSelf = optBoolean("isSelf"),
        className = optString("className"),
        studentNo = optString("studentNo"),
    )
}

private fun EkwingAnswerPaper.toJson(): JSONObject {
    return JSONObject().apply {
        put("key", key)
        put("title", title)
        put("summary", summary)
    }
}

private fun JSONObject.toAnswerPaper(): EkwingAnswerPaper {
    return EkwingAnswerPaper(
        key = optString("key"),
        title = optString("title"),
        summary = optString("summary"),
    )
}

private fun EkwingAnswerSection.toJson(): JSONObject {
    return JSONObject().apply {
        put("title", title)
        put("category", category)
        put("originalText", originalText)
        put("questions", JSONArray().apply { questions.forEach { put(it.toJson()) } })
    }
}

private fun JSONObject.toAnswerSection(): EkwingAnswerSection {
    return EkwingAnswerSection(
        title = optString("title"),
        category = optString("category"),
        originalText = optString("originalText"),
        questions = optJSONArray("questions").jsonObjects().map { it.toAnswerQuestion() },
    )
}

private fun EkwingAnswerQuestion.toJson(): JSONObject {
    return JSONObject().apply {
        put("order", order)
        put("question", question)
        put("answer", answer)
    }
}

private fun JSONObject.toAnswerQuestion(): EkwingAnswerQuestion {
    return EkwingAnswerQuestion(
        order = optString("order"),
        question = optString("question"),
        answer = optString("answer"),
    )
}

private fun EkwingAnswerParseState.toJson(): JSONObject {
    return JSONObject().apply {
        put("status", status.name)
        put("errorMessage", errorMessage ?: JSONObject.NULL)
    }
}

private fun JSONObject.toParseState(): EkwingAnswerParseState {
    val status = runCatching { EkwingAnswerParseStatus.valueOf(optString("status")) }
        .getOrDefault(EkwingAnswerParseStatus.Pending)
    val restoredStatus = if (status == EkwingAnswerParseStatus.Loading) EkwingAnswerParseStatus.Pending else status
    return EkwingAnswerParseState(
        status = restoredStatus,
        errorMessage = optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
    )
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    keys().forEach { key ->
        optString(key).takeIf { it.isNotBlank() }?.let { result[key] = it }
    }
    return result
}

class EkwingAnswerReader(private val context: Context) {
    fun readPapers(source: EkwingAnswerSource): List<EkwingAnswerPaper> {
        val history = source.isHistory
        val isHomework = source.isHomework
        val session = EkwingLoginStore.loadSession(context)
            ?: throw RuntimeException("请先登录翼课账号")

        val useBasic = EkwingLoginStore.useBasicApi(context)
        val taskList = if (isHomework) {
            getHomeworkList(
                session = session,
                useBasic = useBasic,
                history = history,
                allPages = EkwingLoginStore.loadAllPages(context),
            )
        } else {
            getExamList(
                session = session,
                useBasic = useBasic,
                history = history,
                allPages = EkwingLoginStore.loadAllPages(context),
            )
        }
        val allItems = taskList.tasks.distinctBy {
            if (isHomework) it.homeworkAnswerPaperKey() else it.answerPaperKey()
        }
        val papers = allItems.mapIndexed { index, task ->
            if (isHomework) task.toHomeworkAnswerPaper(index) else task.toAnswerPaper(index)
        }

        val oldState = EkwingAnswerState.sourceState(source)
        val taskByPaperKey = allItems.associate { task ->
            val key = if (isHomework) task.homeworkAnswerPaperKey() else task.answerPaperKey()
            key to task.toString()
        }
        val paperKeys = papers.map { it.key }.toSet()
        val sectionsByPaperKey = oldState.sectionsByPaperKey.filterKeys { it in paperKeys }
        val classScoreReportByPaperKey = oldState.classScoreReportByPaperKey.filterKeys { it in paperKeys }
        val parseStateByPaperKey = papers.associate { paper ->
            val existing = oldState.parseStateByPaperKey[paper.key]
            val state = when {
                sectionsByPaperKey.containsKey(paper.key) -> EkwingAnswerParseState(EkwingAnswerParseStatus.Ready)
                existing?.status == EkwingAnswerParseStatus.Loading -> EkwingAnswerParseState(EkwingAnswerParseStatus.Pending)
                existing != null -> existing
                else -> EkwingAnswerParseState(EkwingAnswerParseStatus.Pending)
            }
            paper.key to state
        }
        val newState = EkwingAnswerSourceState(
            papers = papers,
            taskByPaperKey = taskByPaperKey,
            sectionsByPaperKey = sectionsByPaperKey,
            parseStateByPaperKey = parseStateByPaperKey,
            classScoreReportByPaperKey = classScoreReportByPaperKey,
        )
        EkwingAnswerState.setSourceState(source, newState)
        EkwingAnswerCacheStore.saveSourceState(context, source, newState)
        EkwingAnswerPrefetchManager.enqueueAll(context, source, papers)
        return papers
    }

    fun readPapers(history: Boolean): List<EkwingAnswerPaper> {
        val source = if (history) EkwingAnswerSource.History else EkwingAnswerSource.Current
        return readPapers(source)
    }

    fun readAnswerSections(paperKey: String): List<EkwingAnswerSection> {
        return readAnswerSections(EkwingAnswerSource.Current, paperKey)
    }

    fun readAnswerSections(
        source: EkwingAnswerSource,
        paperKey: String,
    ): List<EkwingAnswerSection> {
        val cachedSections = EkwingAnswerState.sourceState(source).sectionsByPaperKey[paperKey]
        val cachedReport = EkwingAnswerState.sourceState(source).classScoreReportByPaperKey[paperKey]
        if (cachedSections != null) {
            if (cachedReport == null) {
                val examText = EkwingAnswerState.sourceState(source).taskByPaperKey[paperKey]
                val task = examText?.let { ekwingReadAttempt { JSONObject(it) }.getOrNull() }
                if (task != null) {
                    val report = extractClassScoreReport(task)
                    if (report != null) {
                        EkwingAnswerState.updateSource(source) { state ->
                            state.copy(classScoreReportByPaperKey = state.classScoreReportByPaperKey + (paperKey to report))
                        }
                        EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
                    }
                }
            }
            return cachedSections
        }

        val examText = EkwingAnswerState.sourceState(source).taskByPaperKey[paperKey]
            ?: throw RuntimeException("未找到任务，请先读取任务列表")
        val task = ekwingReadAttempt { JSONObject(examText) }
            .getOrElse { throw RuntimeException("任务缓存无效，请重新读取列表") }
        val session = EkwingLoginStore.loadSession(context)
            ?: throw RuntimeException("请先登录翼课账号")
        val useBasic = EkwingLoginStore.useBasicApi(context)
        val sections = ekwingReadAttempt {
            if (source.isHomework) {
                readHomeworkSections(session, task, useBasic, source = source, paperKey = paperKey)
            } else {
                readExamSections(session, task, useBasic, source = source, paperKey = paperKey)
            }
        }.getOrElse { error ->
            listOf(EkwingAnswerAssembler.readFailureSection(error))
        }
        val parseState = if (sections.any { it.category == "error" }) {
            EkwingAnswerParseState(
                status = EkwingAnswerParseStatus.Failed,
                errorMessage = sections.firstOrNull()?.questions?.firstOrNull()?.answer,
            )
        } else {
            EkwingAnswerParseState(EkwingAnswerParseStatus.Ready)
        }
        EkwingAnswerState.updateSource(source) {
            it.copy(
                sectionsByPaperKey = it.sectionsByPaperKey + (paperKey to sections),
                parseStateByPaperKey = it.parseStateByPaperKey + (paperKey to parseState),
            )
        }
        EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
        return sections
    }

    private fun getHomeworkList(
        session: EkwingLoginSession,
        useBasic: Boolean,
        history: Boolean,
        allPages: Boolean,
    ): EkwingTaskList {
        val data = getHomeworkListData(session, useBasic, history, allPages)
        return EkwingTaskList(session, EkwingTaskListParser.homeworkListTasks(data))
    }

    private fun getHomeworkListData(
        session: EkwingLoginSession,
        useBasic: Boolean,
        history: Boolean,
        allPages: Boolean,
    ): Any? {
        val paths = EkwingHomeworkApiPlanner.homeworkListPaths(useBasic)
        fun requestPage(page: Int, archiveId: String? = null): Any? {
            return requestFirstSuccessful(paths) { path ->
                val request = EkwingHomeworkRequestPlanner.homeworkListRequest(
                    history = history,
                    page = page,
                    archiveId = archiveId,
                    commonParams = commonParams(session),
                )
                postForm(path, request.payload).opt("data")
            }
        }

        val firstPage = requestPage(1)
        if (!allPages) return firstPage

        val items = EkwingTaskListParser.homeworkListTasks(firstPage).toMutableList()
        val pageInfo = EkwingHomeworkDetailParser.pageInfo(firstPage)
        var current = pageInfo?.optInt("currentPage", 1) ?: 1
        val total = pageInfo?.optInt("totalPage", current) ?: current
        while (current < total && items.isNotEmpty()) {
            val archiveId = lastArchiveId(items)
            current += 1
            val nextItems = EkwingTaskListParser.homeworkListTasks(requestPage(current, archiveId))
            if (nextItems.isEmpty()) break
            items += nextItems
        }
        return JSONArray().apply { items.forEach(::put) }
    }

    private fun getExamList(
        session: EkwingLoginSession,
        useBasic: Boolean,
        history: Boolean,
        allPages: Boolean,
    ): EkwingTaskList {
        val data = getExamListData(session, useBasic, history, allPages)
        return EkwingTaskList(
            session = session,
            tasks = if (history) {
                EkwingTaskListParser.examHistoryTasks(data)
            } else {
                EkwingTaskListParser.studyCenterExamTasks(data)
            },
        )
    }

    private fun getExamListData(
        session: EkwingLoginSession,
        useBasic: Boolean,
        history: Boolean,
        allPages: Boolean,
    ): Any? {
        if (!history) {
            return requestFirstSuccessful(EkwingHomeworkApiPlanner.studyCenterPaths(useBasic)) { path ->
                postForm(path, commonParams(session)).opt("data")
            }
        }

        val paths = EkwingHomeworkApiPlanner.examHistoryPaths(useBasic)
        val firstPage = requestFirstSuccessful(paths) { path ->
            val request = EkwingHomeworkRequestPlanner.examHistoryRequest(
                page = 1,
                commonParams = commonParams(session),
            )
            postForm(path, request.payload).opt("data")
        }
        if (!allPages) return firstPage

        val items = EkwingTaskListParser.examHistoryTasks(firstPage).toMutableList()
        val pageInfo = EkwingHomeworkDetailParser.pageInfo(firstPage)
        var current = pageInfo?.optInt("currentPage", 1) ?: 1
        val total = pageInfo?.optInt("totalPage", current) ?: current
        while (current < total && items.isNotEmpty()) {
            val archiveId = lastArchiveId(items)
            current += 1
            val pageData = requestFirstSuccessful(paths) { path ->
                val request = EkwingHomeworkRequestPlanner.examHistoryRequest(
                    page = current,
                    archiveId = archiveId,
                    commonParams = commonParams(session),
                )
                postForm(path, request.payload).opt("data")
            }
            val nextItems = EkwingTaskListParser.examHistoryTasks(pageData)
            if (nextItems.isEmpty()) break
            items += nextItems
        }
        return JSONArray().apply {
            items.forEach(::put)
        }
    }

    private fun readHomeworkSections(
        session: EkwingLoginSession,
        homework: JSONObject,
        useBasic: Boolean,
        source: EkwingAnswerSource = EkwingAnswerSource.HomeworkCurrent,
        paperKey: String? = null,
    ): List<EkwingAnswerSection> {
        if (isExamPayload(homework)) {
            return readExamSections(session, homework, useBasic, source = source, paperKey = paperKey)
        }
        val detailItems = getDetailItems(session, homework, useBasic)
        if (detailItems.isEmpty()) {
            return listOf(
                EkwingAnswerSection(
                    title = "答案",
                    category = "empty",
                    originalText = "未读取到作业小项",
                    questions = emptyList(),
                )
            )
        }

        return detailItems.flatMapIndexed { index, item ->
            if (isExamPayload(item)) {
                val examSections = ekwingReadAttempt {
                    readExamSections(session, item, useBasic, source = source)
                }.getOrNull()
                if (!examSections.isNullOrEmpty()) {
                    return@flatMapIndexed examSections
                }
            }
            val contentQuestions = fetchContentQuestions(session, homework, item)
            val itemAnswers = parseAnswerQuestions(item)
            val fetchedAnswers = fetchAnswers(session, homework, item)
            val answers = mergeAnswerSources(itemAnswers, fetchedAnswers)
            val questions = mergeQuestions(contentQuestions, answers)
            val title = firstNonBlank(
                item.optString("type_name"),
                item.optString("title"),
                "小项 ${index + 1}",
            ) ?: "小项 ${index + 1}"
            listOf(
                EkwingAnswerSection(
                    title = title,
                    category = firstNonBlank(item.optString("tk_biz"), item.optString("type")) ?: "answer",
                    originalText = firstNonBlank(
                        item.optString("name"),
                        item.optString("title"),
                        contentQuestions.firstOrNull()?.question,
                        item.optString("type_name"),
                        "已读取翼课作业小项",
                    ) ?: "已读取翼课作业小项",
                    questions = questions.ifEmpty {
                        listOf(
                            EkwingAnswerQuestion(
                                order = "Q${index + 1}",
                                question = title,
                                answer = "未解析到标准答案",
                            )
                        )
                    },
                )
            )
        }
    }

    private fun readExamSections(
        session: EkwingLoginSession,
        exam: JSONObject,
        useBasic: Boolean,
        source: EkwingAnswerSource = EkwingAnswerSource.Current,
        paperKey: String? = null,
    ): List<EkwingAnswerSection> {
        val selfId = examSelfId(exam) ?: throw RuntimeException("选中的考试缺少 self_id")
        val examItem = ekwingReadAttempt {
            val request = EkwingHomeworkRequestPlanner.examItemRequest(
                selfId = selfId,
                commonParams = commonParams(session),
            )
            postForm(request.path, request.payload).opt("data")
        }.getOrNull()
        val scoreInfo = ekwingReadAttempt {
            val request = EkwingHomeworkRequestPlanner.examScoreRequest(
                exam = exam,
                selfId = selfId,
                useBasic = useBasic,
                commonParams = commonParams(session),
            )
            postScoreForm(request.path, request.payload)
        }.getOrElse { error ->
            throw RuntimeException("考试成绩读取失败：${error.message}")
        }

        val classReport = extractClassScoreReport(scoreInfo, exam)
        if (classReport != null) {
            val key = paperKey ?: exam.answerPaperKey()
            EkwingAnswerState.updateSource(source) { state ->
                state.copy(classScoreReportByPaperKey = state.classScoreReportByPaperKey + (key to classReport))
            }
            EkwingAnswerCacheStore.saveSourceState(context, source, EkwingAnswerState.sourceState(source))
        }

        val modelScoreInfos = fetchModelScoreInfos(session, selfId, scoreInfo)
        val examTitle = firstNonBlank(exam.optString("title"), exam.optString("self_title"), "考试答案")
            ?: "考试答案"
        val examSummary = exam.toAnswerPaper(0).summary

        val parsedModelSections = (EkwingResolverGateway.examModelSections(modelScoreInfos)
            ?: parseJsonExamSections(modelScoreInfos, examTitle, examSummary))
            .filter { it.questions.isNotEmpty() }
        if (parsedModelSections.isNotEmpty()) {
            return parsedModelSections
        }

        val parsedScoreSections = (EkwingResolverGateway.scorePageSections(scoreInfo)
            ?: parseScoreInfoExamSections(scoreInfo, examTitle, examSummary))
            .filter { it.questions.isNotEmpty() }
        if (parsedScoreSections.isNotEmpty()) {
            return parsedScoreSections
        }

        val questions = examAnswerQuestions(scoreInfo, modelScoreInfos).ifEmpty {
            EkwingAnswerParser.parseHomeworkAnswerQuestions(
                JSONObject().apply {
                    put("exam_item", examItem ?: JSONObject.NULL)
                    put("score_info", scoreInfo)
                    put("model_score_infos", modelScoreInfos)
                }
            )
        }
        return listOf(
            EkwingAnswerSection(
                title = examTitle,
                category = "exam",
                originalText = examSummary,
                questions = questions.ifEmpty {
                    listOf(
                        EkwingAnswerQuestion(
                            order = "Q1",
                            question = "考试答案",
                            answer = "未解析到标准答案",
                        )
                    )
                },
            )
        )
    }

    private fun getDetailItems(session: EkwingLoginSession, homework: JSONObject, useBasic: Boolean): List<JSONObject> {
        val paths = EkwingHomeworkApiPlanner.detailPaths(useBasic)
        var lastError: Throwable? = null
        val mergedItems = linkedMapOf<String, JSONObject>()
        paths.forEach { path ->
            val items = ekwingReadAttempt {
                getDetailItemsFromPath(session, homework, path)
            }.onFailure { error ->
                lastError = error
            }.getOrNull()
            items.orEmpty().forEach { item ->
                val key = EkwingHomeworkDetailParser.detailItemKey(item)
                mergedItems[key] = mergeJsonObjects(mergedItems[key], item)
            }
        }
        if (mergedItems.isNotEmpty()) return mergedItems.values.toList()
        lastError?.let { throw it }
        return emptyList()
    }

    private fun getDetailItemsFromPath(
        session: EkwingLoginSession,
        homework: JSONObject,
        path: String,
    ): List<JSONObject> {
        val request = EkwingHomeworkRequestPlanner.detailItemsRequest(
            homework = homework,
            path = path,
            page = 1,
            commonParams = commonParams(session),
        )
        val payload = request.payload
        val data = postForm(path, payload).opt("data").takeUnless { it == JSONObject.NULL }
            ?: throw RuntimeException("作业详情返回缺少 data")
        val items = EkwingHomeworkDetailParser.detailItems(data).toMutableList()
        val pageInfo = EkwingHomeworkDetailParser.pageInfo(data)
        var current = pageInfo?.optInt("currentPage", 1) ?: 1
        val total = pageInfo?.optInt("totalPage", current) ?: current
        while (current < total) {
            current += 1
            val nextRequest = EkwingHomeworkRequestPlanner.detailItemsRequest(
                homework = homework,
                path = path,
                page = current,
                commonParams = commonParams(session),
            )
            val nextData = postForm(path, nextRequest.payload).opt("data").takeUnless { it == JSONObject.NULL }
                ?: throw RuntimeException("作业详情返回缺少 data")
            val nextItems = EkwingHomeworkDetailParser.detailItems(nextData)
            if (nextItems.isEmpty()) break
            items += nextItems
        }
        return items
    }

    private fun fetchContentQuestions(
        session: EkwingLoginSession,
        homework: JSONObject,
        item: JSONObject,
    ): List<EkwingAnswerQuestion> {
        return ekwingReadAttempt {
            val request = EkwingHomeworkRequestPlanner.contentRequest(
                homework = homework,
                item = item,
                commonParams = commonParams(session),
            )
            postForm(request.path, request.payload).opt("data")
        }.recoverReadFailure {
            fetchItemUrlData(session, item)
        }.map { data ->
            parseContentQuestions(data)
        }.getOrDefault(emptyList())
    }

    private fun fetchItemUrlData(session: EkwingLoginSession, item: JSONObject): Any? {
        val rawUrl = item.optString("url").takeIf { it.isNotBlank() }
            ?: throw RuntimeException("作业小项没有 url")
        val url = rawUrl.absoluteEkwingUrl().withQueryDefaults(commonParams(session))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Mobile Safari/537.36 EkwingStudent/5.2.7",
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw ekwingRequestError("作业页面请求失败 ${connection.responseCode}：${text.take(160)}", connection.responseCode)
            }
            val body = ekwingReadAttempt { JSONObject(text) }.getOrNull()
            if (body != null && body.optInt("status", 0) != 0) {
                val error = ekwingRequestError(errorMessage(body))
                if (error is EkwingTokenExpiredException) throw error
            }
            body ?: ekwingReadAttempt { JSONArray(text) }.getOrDefault(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchAnswers(
        session: EkwingLoginSession,
        homework: JSONObject,
        item: JSONObject,
    ): List<EkwingAnswerQuestion> {
        val records = linkedMapOf<String, EkwingAnswerQuestion>()
        EkwingHomeworkRequestPlanner.answerRequests(
            homework = homework,
            item = item,
            commonParams = commonParams(session),
        ).forEach { request ->
            ekwingReadAttempt {
                postForm(request.path, request.payload).opt("data")
            }.onSuccess { data ->
                parseAnswerQuestions(data).forEach { question ->
                    val key = "${question.question}|${question.answer}"
                    records.putIfAbsent(key, question)
                }
            }
        }
        return records.values.mapIndexed { index, question ->
            question.copy(order = "Q${index + 1}")
        }
    }

    private fun fetchModelScoreInfos(
        session: EkwingLoginSession,
        selfId: String,
        scoreInfo: Any?,
    ): JSONArray {
        val records = JSONArray()
        extractModelScoreRequests(scoreInfo).forEach { request ->
            ekwingReadAttempt {
                val payload = commonParams(session).toMutableMap()
                queryParams(request.optString("url")).forEach { (key, value) ->
                    payload[key] = value
                }
                payload["self_id"] = firstNonBlank(request.optString("self_id"), selfId).orEmpty()
                request.optString("model_id").takeIf { it.isNotBlank() }?.let { payload["model_id"] = it }
                val path = request.optString("path").takeIf { it.isNotBlank() } ?: GET_MODEL_SCORE_PATH
                JSONObject().apply {
                    put("ok", true)
                    put("request", request)
                    put("path", path)
                    put("payload", JSONObject(payload))
                    put("data", getScorePage(path, payload) ?: JSONObject.NULL)
                }
            }.onSuccess { records.put(it) }
        }
        return records
    }

    private fun getScorePage(path: String, data: Map<String, String>): Any? {
        val url = "$EK_BASE_URL$path".withQueryDefaults(data)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw ekwingRequestError("接口请求失败 ${connection.responseCode}：${text.take(160)}", connection.responseCode)
            }
            parseScoreResponse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun postScoreForm(path: String, data: Map<String, String>): Any? {
        val connection = (URL("$EK_BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            val form = data.entries.joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw ekwingRequestError("接口请求失败 ${connection.responseCode}：${text.take(160)}", connection.responseCode)
            }
            parseScoreResponse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseScoreResponse(text: String): Any? {
        val body = ekwingReadAttempt { JSONObject(text) }.getOrNull()
        if (body != null) {
            if (body.optInt("status", -1) != 0) {
                throw ekwingRequestError(errorMessage(body))
            }
            return normalizeJson(body.opt("data").takeUnless { it == JSONObject.NULL })
        }
        return extractScoreJsonFromText(text) ?: normalizeJson(text)
    }

    private fun parseAnswerQuestions(value: Any?): List<EkwingAnswerQuestion> {
        return EkwingAnswerParser.parseHomeworkAnswerQuestions(value)
    }

    private fun parseContentQuestions(value: Any?): List<EkwingAnswerQuestion> {
        return EkwingAnswerParser.parseContentQuestions(value)
    }

    private fun mergeQuestions(
        contentQuestions: List<EkwingAnswerQuestion>,
        answerQuestions: List<EkwingAnswerQuestion>,
    ): List<EkwingAnswerQuestion> {
        if (contentQuestions.isEmpty()) return answerQuestions
        if (answerQuestions.isEmpty()) return contentQuestions
        val count = maxOf(contentQuestions.size, answerQuestions.size)
        return (0 until count).map { index ->
            val content = contentQuestions.getOrNull(index)
            val answer = answerQuestions.getOrNull(index)
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstNonBlank(content?.question, answer?.question) ?: "题目 ${index + 1}",
                answer = firstNonBlank(answer?.answer, content?.answer) ?: "未解析到标准答案",
            )
        }
    }

    private fun mergeAnswerSources(
        first: List<EkwingAnswerQuestion>,
        second: List<EkwingAnswerQuestion>,
    ): List<EkwingAnswerQuestion> {
        val records = linkedMapOf<String, EkwingAnswerQuestion>()
        (first + second).forEach { question ->
            val key = "${question.question}|${question.answer}"
            records.putIfAbsent(key, question)
        }
        return records.values.mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
    }

    private fun commonParams(session: EkwingLoginSession): Map<String, String> {
        return mapOf(
            "v" to "5.1.0",
            "is_http" to "1",
            "os" to "Android",
            "client" to "student",
            "up_version" to "1.0",
            "osv" to (Build.VERSION.RELEASE ?: "Android"),
            "driverCode" to "5.2.7",
            "driverType" to (Build.MODEL ?: "Android"),
            "deviceToken" to deviceToken(),
            "uid" to session.uid,
            "author_id" to session.uid,
            "token" to session.token,
        )
    }

    private fun deviceToken(): String {
        return Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "android-device"
    }

    private fun postForm(path: String, data: Map<String, String>): JSONObject {
        val connection = (URL("$EK_BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            val form = data.entries.joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.use(::readText)
            if (connection.responseCode !in 200..299) {
                throw ekwingRequestError("接口请求失败 ${connection.responseCode}：${text.take(160)}", connection.responseCode)
            }
            val body = ekwingReadAttempt { JSONObject(text) }
                .getOrElse { throw RuntimeException("接口返回不是 JSON：${text.take(160)}") }
            if (body.optInt("status", -1) != 0) {
                throw ekwingRequestError(errorMessage(body))
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: JSONObject): String {
        val data = body.optJSONObject("data")
        if (data != null) {
            for (key in listOf("msg", "error_msg", "errlog")) {
                val message = data.optString(key)
                if (message.isNotBlank()) return message
            }
            return data.toString()
        }
        return body.toString()
    }
}

internal data class EkwingAnswerReadResult(
    val papers: List<EkwingAnswerPaper>,
    val sectionsByPaperKey: Map<String, List<EkwingAnswerSection>>,
)

internal data class EkwingTaskList(
    val session: EkwingLoginSession,
    val tasks: List<JSONObject>,
)

internal object EkwingSessionRefreshPlanner {
    fun actualSession(
        current: EkwingLoginSession,
        refreshed: EkwingLoginSession?,
    ): EkwingLoginSession {
        return refreshed ?: current
    }
}

internal sealed class EkwingReloginPlan {
    data object None : EkwingReloginPlan()

    data class Account(
        val username: String,
        val password: String,
    ) : EkwingReloginPlan()

    data class RealName(
        val name: String,
        val password: String,
        val schoolName: String,
        val schoolId: String,
    ) : EkwingReloginPlan()
}

internal object EkwingReloginPlanner {
    fun plan(identity: EkwingLoginIdentity): EkwingReloginPlan {
        val password = identity.password?.takeIf { it.isNotBlank() } ?: return EkwingReloginPlan.None
        return when (identity.loginMethod) {
            "account" -> {
                val username = identity.username?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                EkwingReloginPlan.Account(
                    username = username.trim(),
                    password = password,
                )
            }
            "real-name" -> {
                val name = identity.name?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                val schoolName = identity.schoolName?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                val schoolId = identity.schoolId?.takeIf { it.isNotBlank() }
                    ?: return EkwingReloginPlan.None
                EkwingReloginPlan.RealName(
                    name = name.trim(),
                    password = password,
                    schoolName = schoolName.trim(),
                    schoolId = schoolId.trim(),
                )
            }
            else -> EkwingReloginPlan.None
        }
    }
}

internal object EkwingAnswerAssembler {
    fun buildAnswerState(
        homeworks: List<JSONObject>,
        readSections: (JSONObject) -> List<EkwingAnswerSection>,
    ): EkwingAnswerReadResult {
        val sectionsByKey = linkedMapOf<String, List<EkwingAnswerSection>>()
        val papers = homeworks.mapIndexed { index, homework ->
            val paper = homework.toAnswerPaper(index)
            sectionsByKey[paper.key] = runCatching {
                readSections(homework)
            }.getOrElse { error ->
                listOf(readFailureSection(error))
            }
            paper
        }
        return EkwingAnswerReadResult(
            papers = papers,
            sectionsByPaperKey = sectionsByKey,
        )
    }

    fun readFailureSection(error: Throwable): EkwingAnswerSection {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
        return EkwingAnswerSection(
            title = "读取失败",
            category = "error",
            originalText = message,
            questions = listOf(
                EkwingAnswerQuestion(
                    order = "Q1",
                    question = "读取失败",
                    answer = message,
                )
            ),
        )
    }
}

internal object EkwingTaskListParser {
    fun homeworkListTasks(value: Any?): List<JSONObject> {
        return extractList(value)
    }

    fun studyCenterTasks(value: Any?): List<JSONObject> {
        return extractList(value)
            .filter { it.optString("type").trim().equals("hw", ignoreCase = true) }
    }

    fun studyCenterExamTasks(value: Any?): List<JSONObject> {
        return extractList(value)
            .filter { it.optString("type").trim().equals("exam", ignoreCase = true) }
    }

    fun examHistoryTasks(value: Any?): List<JSONObject> {
        return extractList(value)
    }

    private fun extractList(value: Any?): List<JSONObject> {
        return when (val normalized = normalizeJson(value)) {
            is JSONArray -> normalized.jsonObjects()
            is JSONObject -> {
                val found = mutableListOf<JSONObject>()
                fun visit(item: Any?) {
                    when (item) {
                        is JSONArray -> found += item.jsonObjects()
                        is JSONObject -> item.keys().forEach { key -> visit(item.opt(key)) }
                    }
                }
                visit(normalized)
                found
            }
            else -> emptyList()
        }
    }
}

internal object EkwingHomeworkDetailParser {
    fun detailItems(value: Any?): List<JSONObject> {
        val normalized = normalizeJson(value)
        return when (normalized) {
            is JSONArray -> normalized.jsonObjects()
            is JSONObject -> {
                DETAIL_LIST_KEYS.asSequence()
                    .mapNotNull { key -> normalized.opt(key).takeUnless { it == JSONObject.NULL } }
                    .mapNotNull { candidate -> detailItems(candidate).takeIf { it.isNotEmpty() } }
                    .firstOrNull()
                    ?: collectNestedDetailItems(normalized)
            }
            else -> emptyList()
        }
    }

    fun pageInfo(value: Any?): JSONObject? {
        val normalized = normalizeJson(value)
        if (normalized !is JSONObject) return null
        normalized.optJSONObject("page")?.let { return normalizePageInfo(it) }
        val page = JSONObject()
        var hasPageValue = false
        DETAIL_PAGE_KEYS.forEach { key ->
            normalized.opt(key).takeUnless { it == JSONObject.NULL }?.let { value ->
                page.put(key, value)
                hasPageValue = true
            }
        }
        return page.takeIf { hasPageValue }?.let(::normalizePageInfo)
    }

    private fun normalizePageInfo(page: JSONObject): JSONObject {
        val normalized = JSONObject(page.toString())
        firstNonBlank(*DETAIL_CURRENT_PAGE_KEYS.map { key -> page.optString(key) }.toTypedArray())
            ?.let { normalized.put("currentPage", it) }
        firstNonBlank(*DETAIL_TOTAL_PAGE_KEYS.map { key -> page.optString(key) }.toTypedArray())
            ?.let { normalized.put("totalPage", it) }
        return normalized
    }

    private fun collectNestedDetailItems(value: JSONObject): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        fun visit(item: Any?) {
            when (item) {
                is JSONObject -> {
                    if (looksLikeDetailItem(item)) {
                        found += item
                    } else {
                        item.keys().forEach { key -> visit(item.opt(key)) }
                    }
                }
                is JSONArray -> {
                    for (index in 0 until item.length()) visit(item.opt(index))
                }
            }
        }
        visit(value)
        return found.distinctBy { detailItemKey(it) }
    }

    private fun looksLikeDetailItem(item: JSONObject): Boolean {
        return (item.has("id") || item.has("hwcid") || item.has("hw_cnt_id") || item.has("hwcnt_id")) &&
            DETAIL_ITEM_HINT_KEYS.any { item.has(it) }
    }

    fun detailItemKey(item: JSONObject): String {
        return firstNonBlank(
            homeworkItemContentId(item),
            item.optString("id"),
            item.optString("hwcid"),
            item.optString("hw_cnt_id"),
            item.optString("hwcnt_id"),
            item.optString("record_id"),
            item.optString("hid"),
            item.optString("hw_id"),
            item.toString(),
        ) ?: item.toString()
    }
}

private fun mergeJsonObjects(first: JSONObject?, second: JSONObject): JSONObject {
    if (first == null) return JSONObject(second.toString())
    val merged = JSONObject(first.toString())
    second.keys().forEach { key ->
        val value = second.opt(key)
        if (value != JSONObject.NULL) {
            merged.put(key, value)
        }
    }
    return merged
}

internal object EkwingAnswerParser {
    fun parseHomeworkAnswerQuestions(value: Any?): List<EkwingAnswerQuestion> {
        // so 优先：解析口径单一事实源（ekwing_resolver）；so 不可用或返回为空时回退本地实现
        EkwingResolverGateway.homeworkQuestions(value)?.let { return it }
        val normalized = normalizeJson(value)
        val answerRoot = (normalized as? JSONObject)?.optJSONObject("ans")
        val answers = answerRoot?.optJSONArray("answers")
        if (answers != null && answers.length() > 0) {
            return parseHomeworkAnswerItems(answers, answerRoot.optString("txt"))
        }

        val itemContainer = homeworkAnswerItems(normalized)
        if (itemContainer != null && itemContainer.length() > 0) {
            return parseHomeworkAnswerItems(itemContainer)
        }

        return findAnswerLikeObjects(normalized).mapIndexed { index, item ->
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstQuestionText(item) ?: "题目 ${index + 1}",
                answer = flattenAnswer(item).joinToString("\n").ifBlank { item.toString() },
            )
        }
    }

    fun parseContentQuestions(value: Any?): List<EkwingAnswerQuestion> {
        val normalized = normalizeJson(value)
        return findQuestionLikeObjects(normalized).mapIndexed { index, item ->
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstQuestionText(item) ?: "题目 ${index + 1}",
                answer = flattenAnswer(item, strict = true).joinToString("\n"),
            )
        }
    }

    fun parseExamAnswerQuestions(value: Any?): List<EkwingAnswerQuestion> {
        val questions = mutableListOf<EkwingAnswerQuestion>()
        collectExamQuestions(normalizeJson(value), questions)
        return questions.distinctBy { "${it.question}|${it.answer}" }
            .mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
    }
}

internal fun examAnswerQuestions(scoreInfo: Any?, modelScoreInfos: Any?): List<EkwingAnswerQuestion> {
    val modelQuestions = parseJsonExamAnswers(JSONObject().apply {
        put("model_score_infos", normalizeJson(modelScoreInfos) ?: JSONArray())
    })
    val modelQuestionsWithAnswers = modelQuestions.filter { it.answer.isNotBlank() }
    if (modelQuestionsWithAnswers.isNotEmpty()) {
        return modelQuestionsWithAnswers.mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
    }
    return EkwingAnswerParser.parseExamAnswerQuestions(scoreInfo)
}

internal fun parseJsonExamAnswers(root: Any?): List<EkwingAnswerQuestion> {
    // so 优先：模块成绩逐题答案（与 ekwing_resolver 真实语料对拍 0 差异的那条口径）
    EkwingResolverGateway.examQuestions(root)?.let { return it }
    val questions = mutableListOf<EkwingAnswerQuestion>()
    extractModelScoreItems(root).forEach { item ->
        val data = (normalizeJson(item.opt("data")) as? JSONObject) ?: item
        val modelInfo = data.optJSONObject("model_info") ?: item.optJSONObject("model_info") ?: return@forEach
        val modelBaseInfo = data.optJSONObject("model_base_info") ?: item.optJSONObject("model_base_info")
        questions += parseJsonModelInfo(modelInfo, modelBaseInfo)
    }
    return questions.mapIndexed { index, question -> question.copy(order = "Q${index + 1}") }
}

internal fun parseJsonExamSections(
    root: Any?,
    examTitle: String = "考试答案",
    examSummary: String = "学习中心考试",
): List<EkwingAnswerSection> {
    val sections = mutableListOf<EkwingAnswerSection>()
    extractModelScoreItems(root).forEachIndexed { index, item ->
        val data = (normalizeJson(item.opt("data")) as? JSONObject) ?: item
        val modelInfo = data.optJSONObject("model_info") ?: item.optJSONObject("model_info") ?: return@forEachIndexed
        val modelBaseInfo = data.optJSONObject("model_base_info") ?: item.optJSONObject("model_base_info")

        val rawModelType = firstNonBlank(modelInfo.optString("model_type"), modelInfo.optString("type"))
        val modelName = firstNonBlank(
            knownModelTypeName(rawModelType),
            titleFromModelBaseInfo(modelBaseInfo),
            modelInfo.optString("model_type_name"),
            modelInfo.optString("name"),
            if (rawModelType == "1") "模仿朗读" else "模块 ${index + 1}",
        ) ?: if (rawModelType == "1") "模仿朗读" else "模块 ${index + 1}"
        val modelType = firstNonBlank(rawModelType, "exam")

        val readingText = firstNonBlank(
            modelInfo.optString("real_text"),
            modelInfo.optString("dis_text"),
            modelInfo.optString("reading_text"),
            modelInfo.optString("text"),
        )

        val isReadingModel = rawModelType == "1" || !readingText.isNullOrBlank()

        if (isReadingModel) {
            val text = readingText.orEmpty()
            sections += EkwingAnswerSection(
                title = modelName,
                category = rawModelType ?: "1",
                originalText = text,
                questions = listOf(
                    EkwingAnswerQuestion(
                        order = "Q1",
                        question = "朗读文本",
                        answer = text,
                    )
                ),
            )
        } else {
            val questions = parseJsonModelInfo(modelInfo, modelBaseInfo).ifEmpty {
                EkwingAnswerParser.parseExamAnswerQuestions(modelInfo).ifEmpty {
                    EkwingAnswerParser.parseHomeworkAnswerQuestions(modelInfo)
                }
            }
            sections += EkwingAnswerSection(
                title = modelName,
                category = rawModelType ?: "exam",
                originalText = firstNonBlank(
                    modelInfo.optString("answer_tip"),
                    modelInfo.optString("desc"),
                    modelName,
                ) ?: modelName,
                questions = questions,
            )
        }
    }
    return sections
}

internal fun extractModelScoreItems(root: Any?): List<JSONObject> {
    return when (val normalized = normalizeJson(root)) {
        is JSONArray -> normalized.jsonObjects()
        is JSONObject -> {
            val list = sequenceOf("model_score_infos", "list", "rows", "data")
                .mapNotNull { normalized.optJSONArray(it) }
                .firstOrNull()
                ?.jsonObjects()
                .orEmpty()
            when {
                list.isNotEmpty() -> list
                normalized.optJSONObject("data")?.optJSONObject("model_info") != null -> listOf(normalized)
                normalized.optJSONObject("model_info") != null -> listOf(JSONObject().apply { put("data", normalized) })
                else -> emptyList()
            }
        }
        else -> emptyList()
    }
}

private fun parseJsonModelInfo(
    modelInfo: JSONObject,
    modelBaseInfo: JSONObject?,
): List<EkwingAnswerQuestion> {
    val modelName = firstNonBlank(
        knownModelTypeName(firstNonBlank(modelInfo.optString("model_type"), modelInfo.optString("type"))),
        titleFromModelBaseInfo(modelBaseInfo),
        modelInfo.optString("model_type_name"),
        modelInfo.optString("name"),
    )
    val quesList = modelInfo.optJSONArray("ques_list")
    if (quesList != null && quesList.length() > 0) {
        return (0 until quesList.length()).mapNotNull { index ->
            val question = quesList.optJSONObject(index) ?: return@mapNotNull null
            val answers = flattenJsonExamAnswer(
                question.opt("answer")
                    ?: question.opt("refText")
                    ?: question.opt("right_answer")
                    ?: question.opt("standard_answer")
                    ?: question.opt("answers")
            )
            if (answers.isEmpty()) return@mapNotNull null
            EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstNonBlank(
                    question.optString("title_text"),
                    question.optString("question"),
                    question.optString("title"),
                    question.optString("text"),
                ) ?: "题目 ${index + 1}",
                answer = answers.joinToString("\n"),
            )
        }
    }

    val directAnswers = flattenJsonExamAnswer(modelInfo.opt("answer"))
    if (directAnswers.isNotEmpty()) {
        return listOf(
            EkwingAnswerQuestion(
                order = "Q1",
                question = firstNonBlank(
                    modelInfo.optString("answer_tip"),
                    modelInfo.optString("title_text"),
                    modelInfo.optString("desc"),
                    modelName,
                ) ?: "题目 1",
                answer = directAnswers.joinToString("\n"),
            )
        )
    }

    val readingText = firstNonBlank(modelInfo.optString("real_text"), modelInfo.optString("dis_text"))
    if (!readingText.isNullOrBlank()) {
        return listOf(
            EkwingAnswerQuestion(
                order = "Q1",
                question = readingText,
                answer = readingText,
            )
        )
    }

    return emptyList()
}

private fun flattenJsonExamAnswer(value: Any?): List<String> {
    val answers = linkedSetOf<String>()
    fun collect(item: Any?) {
        when (val normalized = normalizeJson(item)) {
            null, JSONObject.NULL -> Unit
            is JSONArray -> {
                for (index in 0 until normalized.length()) collect(normalized.opt(index))
            }
            is JSONObject -> {
                listOf("answer", "answers", "right_answer", "standard_answer", "refText", "text", "content")
                    .forEach { key ->
                        if (normalized.has(key)) collect(normalized.opt(key))
                    }
            }
            else -> cleanDisplayText(normalized.toString()).takeIf { it.isNotBlank() }?.let { answers += it }
        }
    }
    collect(value)
    return answers.toList()
}

private fun titleFromModelBaseInfo(modelBaseInfo: JSONObject?): String? {
    if (modelBaseInfo == null) return null
    val titleInfo = modelBaseInfo.optJSONObject("title_info")
    if (titleInfo != null) {
        firstNonBlank(titleInfo.optString("title"), titleInfo.optString("name"))?.let { return it }
    }
    return firstNonBlank(modelBaseInfo.optString("title"), modelBaseInfo.optString("name"))
}

private fun knownModelTypeName(modelType: String?): String? {
    return when (modelType) {
        "1" -> "模仿朗读"
        "6" -> "信息转述"
        "7" -> "听选信息"
        "8" -> "回答问题"
        "9" -> "询问信息"
        else -> null
    }
}

private fun collectExamQuestions(value: Any?, questions: MutableList<EkwingAnswerQuestion>) {
    when (value) {
        is JSONArray -> {
            for (index in 0 until value.length()) collectExamQuestions(value.opt(index), questions)
        }
        is JSONObject -> {
            parseExamModelInfoQuestions(value).takeIf { it.isNotEmpty() }?.let {
                questions += it
                return
            }
            parseExamReportQuestions(value).takeIf { it.isNotEmpty() }?.let {
                questions += it
            }
            if (looksLikeAnswerObject(value)) {
                val answer = formatAnswerText(value)
                if (answer.isNotBlank()) {
                    questions += EkwingAnswerQuestion(
                        order = "Q${questions.size + 1}",
                        question = firstQuestionText(value) ?: "题目 ${questions.size + 1}",
                        answer = answer,
                    )
                }
            }
            value.keys().forEach { key -> collectExamQuestions(value.opt(key), questions) }
        }
    }
}

internal fun parseScoreInfoExamSections(
    scoreInfo: Any?,
    examTitle: String = "考试答案",
    examSummary: String = "学习中心考试",
): List<EkwingAnswerSection> {
    val root = normalizeJson(scoreInfo)
    val candidates = mutableListOf<JSONObject>()
    fun collectCandidates(value: Any?, depth: Int = 0) {
        if (depth > 6) return
        when (val normalized = normalizeJson(value)) {
            is JSONArray -> {
                for (i in 0 until normalized.length()) {
                    collectCandidates(normalized.opt(i), depth + 1)
                }
            }
            is JSONObject -> {
                candidates += normalized
                normalized.optJSONArray("useful_candidates")?.let { arr ->
                    for (i in 0 until arr.length()) collectCandidates(arr.opt(i), depth + 1)
                }
                normalized.opt("data")?.let { collectCandidates(it, depth + 1) }
            }
        }
    }
    collectCandidates(root)

    for (candidate in candidates) {
        val ansInfo = candidate.optJSONObject("ans_info") ?: continue
        val content = ansInfo.optJSONArray("content") ?: continue
        if (content.length() == 0) continue

        val modelTitleMap = mutableMapOf<String, ModelTitleMeta>()
        candidate.optJSONArray("self")?.jsonObjects()?.forEach { item ->
            val modelId = item.optString("model_id").takeIf { it.isNotBlank() } ?: return@forEach
            modelTitleMap[modelId] = ModelTitleMeta(
                title = item.optString("title"),
                modelType = item.optString("model_type"),
                desc = item.optString("desc"),
            )
        }
        candidate.optJSONArray("model_list")?.jsonObjects()?.forEach { item ->
            val modelId = item.optString("model_id").takeIf { it.isNotBlank() } ?: return@forEach
            val existing = modelTitleMap[modelId]
            modelTitleMap[modelId] = ModelTitleMeta(
                title = firstNonBlank(existing?.title, item.optString("title")),
                modelType = firstNonBlank(existing?.modelType, item.optString("model_type")),
                desc = firstNonBlank(existing?.desc, item.optString("desc")),
            )
        }

        val sections = mutableListOf<EkwingAnswerSection>()
        for (sectionIndex in 0 until content.length()) {
            val sectionObj = content.optJSONObject(sectionIndex) ?: continue
            val modelId = sectionObj.optString("model_id")
            val meta = modelTitleMap[modelId]
            val sectionTitle = firstNonBlank(
                sectionObj.optString("title"),
                sectionObj.optString("name"),
                meta?.title,
                knownModelTypeName(meta?.modelType ?: sectionObj.optString("model_type")),
                "部分 ${sectionIndex + 1}",
            ) ?: "部分 ${sectionIndex + 1}"
            val sectionCategory = firstNonBlank(
                sectionObj.optString("model_type"),
                meta?.modelType,
                "exam",
            ) ?: "exam"
            val origText = firstNonBlank(
                sectionObj.optString("desc"),
                meta?.desc,
                sectionObj.optString("tip"),
                sectionTitle,
            ) ?: sectionTitle

            val quesList = sectionObj.optJSONArray("ques_list")
            val questions = mutableListOf<EkwingAnswerQuestion>()
            if (quesList != null) {
                for (qIndex in 0 until quesList.length()) {
                    val q = quesList.optJSONObject(qIndex) ?: continue
                    val answer = firstNonBlank(
                        flattenAnswer(q.opt("refText")).joinToString("\n"),
                        flattenAnswer(firstAnswerValue(q)).joinToString("\n"),
                        firstNonBlank(q.optString("real_text"), q.optString("dis_text")),
                    )
                    if (!answer.isNullOrBlank()) {
                        questions += EkwingAnswerQuestion(
                            order = "Q${questions.size + 1}",
                            question = firstQuestionText(q) ?: "题目 ${questions.size + 1}",
                            answer = answer,
                        )
                    }
                }
            }
            if (questions.isNotEmpty()) {
                sections += EkwingAnswerSection(
                    title = sectionTitle,
                    category = sectionCategory,
                    originalText = origText,
                    questions = questions,
                )
            }
        }
        if (sections.isNotEmpty()) {
            return sections
        }
    }

    for (candidate in candidates) {
        val modelList = candidate.optJSONArray("model_list") ?: continue
        if (modelList.length() == 0) continue
        val sections = mutableListOf<EkwingAnswerSection>()
        for (i in 0 until modelList.length()) {
            val item = modelList.optJSONObject(i) ?: continue
            val questions = mutableListOf<EkwingAnswerQuestion>()
            val quesList = item.optJSONArray("ques_list")
            if (quesList != null) {
                for (qIndex in 0 until quesList.length()) {
                    val q = quesList.optJSONObject(qIndex) ?: continue
                    val answer = firstNonBlank(
                        flattenAnswer(q.opt("refText")).joinToString("\n"),
                        flattenAnswer(firstAnswerValue(q)).joinToString("\n"),
                        firstNonBlank(q.optString("real_text"), q.optString("dis_text")),
                    )
                    if (!answer.isNullOrBlank()) {
                        questions += EkwingAnswerQuestion(
                            order = "Q${questions.size + 1}",
                            question = firstQuestionText(q) ?: "题目 ${questions.size + 1}",
                            answer = answer,
                        )
                    }
                }
            }
            if (questions.isNotEmpty()) {
                val title = firstNonBlank(item.optString("title"), item.optString("name"), "模块 ${i + 1}") ?: "模块 ${i + 1}"
                sections += EkwingAnswerSection(
                    title = title,
                    category = item.optString("model_type").ifBlank { "exam" },
                    originalText = firstNonBlank(item.optString("desc"), title) ?: title,
                    questions = questions,
                )
            }
        }
        if (sections.isNotEmpty()) {
            return sections
        }
    }

    return emptyList()
}

internal fun extractClassScoreReport(vararg sources: Any?): EkwingClassScoreReport? {
    val candidates = mutableListOf<JSONObject>()
    val visited = mutableSetOf<Int>()

    fun collect(value: Any?, depth: Int) {
        if (depth > 8 || value == null) return
        val normalized = normalizeJson(value) ?: return
        when (normalized) {
            is JSONArray -> {
                for (i in 0 until normalized.length()) {
                    collect(normalized.opt(i), depth + 1)
                }
            }
            is JSONObject -> {
                val id = System.identityHashCode(normalized)
                if (!visited.add(id)) return
                candidates += normalized

                normalized.optJSONArray("useful_candidates")?.let { arr ->
                    for (i in 0 until arr.length()) collect(arr.opt(i), depth + 1)
                }
                normalized.optJSONArray("candidates")?.let { arr ->
                    for (i in 0 until arr.length()) collect(arr.opt(i), depth + 1)
                }
                normalized.opt("data")?.let { collect(it, depth + 1) }
                normalized.opt("report")?.let { collect(it, depth + 1) }
                normalized.opt("score_info")?.let { collect(it, depth + 1) }
                normalized.opt("exam_item")?.let { collect(it, depth + 1) }
                normalized.opt("self_info")?.let { collect(it, depth + 1) }
                normalized.opt("exam")?.let { collect(it, depth + 1) }
            }
        }
    }

    for (src in sources) {
        collect(src, 0)
    }

    var bestRankItems = emptyList<EkwingClassRankItem>()
    var bestUserRank: Int? = null
    var bestUserScore: String? = null
    var bestClassName: String? = null

    val rankArrayKeys = listOf(
        "self_ranks", "rank_list", "ranking_list", "ranks", "class_ranks",
        "rankList", "exam_rank", "exam_ranks", "score_ranks", "list"
    )

    for (candidate in candidates) {
        for (key in rankArrayKeys) {
            val arr = candidate.optJSONArray(key) ?: continue
            if (arr.length() == 0) continue

            val parsedItems = mutableListOf<EkwingClassRankItem>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val userInfo = item.optJSONObject("user_info")
                val isSelf = when (val v = item.opt("is_self") ?: item.opt("isSelf") ?: item.opt("self")) {
                    is Boolean -> v
                    is Number -> v.toInt() == 1
                    is String -> v == "1" || v.equals("true", ignoreCase = true)
                    null -> false
                    else -> false
                }
                val index = item.optInt("index", -1).takeIf { it > 0 }
                    ?: item.optInt("rank", -1).takeIf { it > 0 }
                    ?: item.optInt("order", -1).takeIf { it > 0 }
                    ?: (i + 1)
                val name = firstNonBlank(
                    item.optString("real_name"),
                    userInfo?.optString("nicename"),
                    userInfo?.optString("real_name"),
                    userInfo?.optString("name"),
                    item.optString("nicename"),
                    item.optString("name"),
                    item.optString("uname"),
                ) ?: ""
                val score = firstNonBlank(
                    item.optString("score"),
                    item.optString("user_score"),
                    item.optString("stu_score"),
                    item.optString("total_score")
                ) ?: ""
                val className = firstNonBlank(
                    userInfo?.optString("classes"),
                    userInfo?.optString("class_name"),
                    userInfo?.optString("className"),
                    item.optString("classes"),
                    item.optString("class_name"),
                    item.optString("className"),
                ) ?: ""
                val studentNo = firstNonBlank(
                    userInfo?.optString("student_no"),
                    userInfo?.optString("studentNo"),
                    item.optString("student_no"),
                    item.optString("studentNo"),
                ) ?: ""

                if (name.isNotBlank() || score.isNotBlank()) {
                    parsedItems += EkwingClassRankItem(
                        index = index,
                        name = name,
                        score = score,
                        isSelf = isSelf,
                        className = className,
                        studentNo = studentNo,
                    )
                }
            }

            if (parsedItems.size > bestRankItems.size) {
                bestRankItems = parsedItems
            }
        }

        val userRankRaw = candidate.opt("user_rank")
            ?: candidate.opt("userRank")
            ?: candidate.opt("class_rank")
            ?: candidate.opt("classRank")
            ?: candidate.opt("self_rank")
            ?: candidate.opt("selfRank")
            ?: candidate.opt("my_rank")
            ?: candidate.opt("rank")
        val userRankObj = userRankRaw as? JSONObject
        val parsedRank = when {
            userRankObj != null -> {
                userRankObj.optInt("rank", -1).takeIf { it > 0 }
                    ?: userRankObj.optInt("user_rank", -1).takeIf { it > 0 }
                    ?: userRankObj.optInt("index", -1).takeIf { it > 0 }
            }
            userRankRaw is Number -> userRankRaw.toInt().takeIf { it > 0 }
            userRankRaw is String -> userRankRaw.toIntOrNull()?.takeIf { it > 0 }
            else -> null
        }
        if (bestUserRank == null && parsedRank != null) {
            bestUserRank = parsedRank
        }

        val parsedScore = firstNonBlank(
            userRankObj?.optString("score"),
            userRankObj?.optString("user_score"),
            candidate.optString("user_score"),
            candidate.optString("self_score"),
            candidate.optString("score"),
            candidate.optString("stu_score"),
            candidate.optString("get_score"),
        )
        if (bestUserScore.isNullOrBlank() && !parsedScore.isNullOrBlank()) {
            bestUserScore = parsedScore
        }

        val parsedClass = firstNonBlank(
            userRankObj?.optString("classes"),
            userRankObj?.optString("className"),
            userRankObj?.optJSONObject("user_info")?.optString("classes"),
            candidate.optString("classes"),
            candidate.optString("className"),
            candidate.optString("class_name"),
            candidate.optJSONObject("user_info")?.optString("classes"),
        )
        if (bestClassName.isNullOrBlank() && !parsedClass.isNullOrBlank()) {
            bestClassName = parsedClass
        }
    }

    val selfItem = bestRankItems.firstOrNull { it.isSelf }
    val finalUserRank = bestUserRank ?: selfItem?.index
    val finalUserScore = bestUserScore ?: selfItem?.score?.ifBlank { null }
    val finalClassName = bestClassName ?: selfItem?.className?.ifBlank { null }
        ?: bestRankItems.firstOrNull { it.className.isNotBlank() }?.className

    if (finalUserRank != null || !finalUserScore.isNullOrBlank() || !finalClassName.isNullOrBlank() || bestRankItems.isNotEmpty()) {
        return EkwingClassScoreReport(
            userRank = finalUserRank,
            userScore = finalUserScore,
            className = finalClassName,
            ranks = bestRankItems,
        )
    }
    return null
}

private fun isExamPayload(obj: JSONObject): Boolean {
    return obj.optString("type") == "exam" ||
        obj.optString("task_type") == "exam" ||
        obj.optString("self_id").isNotBlank() ||
        obj.optString("last_model_id").isNotBlank()
}

private data class ModelTitleMeta(
    val title: String? = null,
    val modelType: String? = null,
    val desc: String? = null,
)

private fun parseExamReportQuestions(report: JSONObject): List<EkwingAnswerQuestion> {
    val ansInfo = report.optJSONObject("ans_info") ?: return emptyList()
    val content = ansInfo.optJSONArray("content") ?: return emptyList()
    val parsed = mutableListOf<EkwingAnswerQuestion>()
    for (sectionIndex in 0 until content.length()) {
        val section = content.optJSONObject(sectionIndex) ?: continue
        val quesList = section.optJSONArray("ques_list") ?: continue
        for (questionIndex in 0 until quesList.length()) {
            val question = quesList.optJSONObject(questionIndex) ?: continue
            val answer = firstNonBlank(
                flattenAnswer(question.opt("refText")).joinToString("\n"),
                flattenAnswer(firstAnswerValue(question)).joinToString("\n"),
                firstNonBlank(question.optString("real_text"), question.optString("dis_text")),
            )
            if (!answer.isNullOrBlank()) {
                parsed += EkwingAnswerQuestion(
                    order = "Q${parsed.size + 1}",
                    question = firstQuestionText(question) ?: "题目 ${parsed.size + 1}",
                    answer = answer,
                )
            }
        }
    }
    return parsed
}

private fun parseExamModelInfoQuestions(value: JSONObject): List<EkwingAnswerQuestion> {
    val modelInfo = value.optJSONObject("model_info") ?: return emptyList()
    val quesList = modelInfo.optJSONArray("ques_list")
    if (quesList != null && quesList.length() > 0) {
        return parseHomeworkAnswerItems(quesList)
    }
    val answer = firstNonBlank(
        flattenAnswer(firstAnswerValue(modelInfo)).joinToString("\n"),
        firstNonBlank(modelInfo.optString("real_text"), modelInfo.optString("dis_text")),
    )
    if (answer.isNullOrBlank()) return emptyList()
    return listOf(
        EkwingAnswerQuestion(
            order = "Q1",
            question = firstQuestionText(modelInfo) ?: firstNonBlank(modelInfo.optString("model_type_name"), modelInfo.optString("name"))
                ?: "题目 1",
            answer = answer,
        )
    )
}

private fun parseHomeworkAnswerItems(items: JSONArray, fallbackQuestion: String? = null): List<EkwingAnswerQuestion> {
    return (0 until items.length()).mapNotNull { index ->
        val value = items.opt(index)
        when (value) {
            is JSONObject -> EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstQuestionText(value) ?: firstNonBlank(fallbackQuestion) ?: "题目 ${index + 1}",
                answer = formatAnswerText(value),
            )
            null, JSONObject.NULL -> null
            else -> EkwingAnswerQuestion(
                order = "Q${index + 1}",
                question = firstNonBlank(fallbackQuestion) ?: "题目 ${index + 1}",
                answer = flattenAnswer(value).joinToString("\n").ifBlank { value.toString() },
            )
        }
    }
}

private fun homeworkAnswerItems(value: Any?): JSONArray? {
    val containers = setOf(
        "answers",
        "answerList",
        "answer_list",
        "questions",
        "questionList",
        "question_list",
        "list",
        "data",
        "items",
        "records",
        "rows",
        "result",
    )
    fun isAnswerItemArray(array: JSONArray): Boolean {
        return (0 until array.length()).any { index ->
            when (val item = array.opt(index)) {
                is JSONObject -> STANDARD_ANSWER_KEYS.any { item.has(it) } || QUESTION_KEYS.any { item.has(it) }
                null, JSONObject.NULL -> false
                else -> item.toString().isNotBlank()
            }
        }
    }
    fun visit(item: Any?): JSONArray? {
        return when (item) {
            is JSONArray -> item.takeIf(::isAnswerItemArray)
            is JSONObject -> {
                containers.asSequence()
                    .mapNotNull { key -> item.opt(key).takeUnless { it == JSONObject.NULL } }
                    .mapNotNull(::visit)
                    .firstOrNull()
                    ?: item.keys().asSequence()
                        .mapNotNull { key -> visit(item.opt(key)) }
                        .firstOrNull()
            }
            else -> null
        }
    }
    return visit(value)
}

private fun findAnswerLikeObjects(value: Any?): List<JSONObject> {
    val found = mutableListOf<JSONObject>()
    fun visit(item: Any?) {
        when (item) {
            is JSONObject -> {
                if (STANDARD_ANSWER_KEYS.any { item.has(it) }) {
                    found += item
                }
                item.keys().forEach { key -> visit(item.opt(key)) }
            }
            is JSONArray -> {
                for (index in 0 until item.length()) visit(item.opt(index))
            }
        }
    }
    visit(value)
    return found
}

private fun looksLikeAnswerObject(item: JSONObject): Boolean {
    return STANDARD_ANSWER_KEYS.any { item.has(it) }
}

private fun findQuestionLikeObjects(value: Any?): List<JSONObject> {
    val found = mutableListOf<JSONObject>()
    fun visit(item: Any?) {
        when (item) {
            is JSONObject -> {
                if (QUESTION_KEYS.any { item.has(it) }) {
                    found += item
                    return
                }
                item.keys().forEach { key -> visit(item.opt(key)) }
            }
            is JSONArray -> {
                for (index in 0 until item.length()) visit(item.opt(index))
            }
        }
    }
    visit(value)
    return found
}

private fun flattenAnswer(value: Any?, strict: Boolean = false): List<String> {
    val answers = linkedSetOf<String>()
    fun addAnswer(value: String) {
        cleanDisplayText(value).takeIf { it.isNotBlank() }?.let { answers += it }
    }

    fun collect(item: Any?, fromAnswerField: Boolean = false) {
        when (item) {
            null, JSONObject.NULL -> Unit
            is JSONArray -> {
                for (index in 0 until item.length()) collect(item.opt(index), fromAnswerField)
            }
            is JSONObject -> {
                var collected = false
                STANDARD_ANSWER_KEYS.forEach { key ->
                    if (item.has(key)) {
                        collected = true
                        collect(item.opt(key), fromAnswerField = true)
                    }
                }
                if (!collected && (!strict || fromAnswerField)) {
                    firstNonBlank(*ANSWER_TEXT_KEYS.map { key -> item.optString(key) }.toTypedArray())
                        ?.let(::addAnswer)
                }
            }
            else -> item.toString().takeIf { it.isNotBlank() }?.let(::addAnswer)
        }
    }
    collect(normalizeJson(value))
    return answers.toList()
}

private fun formatAnswerText(item: JSONObject): String {
    val primaryAnswer = flattenAnswer(firstAnswerValue(item)).joinToString("\n")
        .ifBlank {
            if (QUESTION_KEYS.any { item.has(it) }) "" else flattenAnswer(item).joinToString("\n")
        }
    return primaryAnswer
}

private fun normalizeJson(value: Any?): Any? {
    if (value is String) {
        val repaired = repairMojibakeText(value)
        val decoded = decodeHtmlEntities(repaired)
        val text = decoded.trim()
        parseJsonCandidate(text)?.let { return normalizeJson(it) }
        decodeUnicodeEscapes(text).takeIf { it != text }?.let { unicodeDecoded ->
            parseJsonCandidate(unicodeDecoded.trim())?.let { return normalizeJson(it) }
        }
        text.urlDecode().takeIf { it != text }?.let { urlDecoded ->
            parseJsonCandidate(urlDecoded.trim())?.let { return normalizeJson(it) }
            extractEmbeddedJson(urlDecoded)?.let { return normalizeJson(it) }
        }
        extractEmbeddedJson(text)?.let { return normalizeJson(it) }
        return repaired
    }
    if (value is JSONObject) {
        val normalized = JSONObject()
        value.keys().forEach { key -> normalized.put(key, normalizeJson(value.opt(key))) }
        return normalized
    }
    if (value is JSONArray) {
        val normalized = JSONArray()
        for (index in 0 until value.length()) normalized.put(normalizeJson(value.opt(index)))
        return normalized
    }
    return value
}

private fun extractEmbeddedJson(text: String): Any? {
    return extractBalancedJson(text, '{') ?: extractBalancedJson(text, '[')
}

internal fun extractScoreJsonFromText(text: String): Any? {
    val decoded = decodeHtmlEntities(repairMojibakeText(text))
    val parseCandidates = extractJsonParseCandidates(decoded)
    val rawCandidates = extractRawJsonCandidates(decoded, maxCandidates = 80)
    val allCandidates = (parseCandidates + rawCandidates).mapNotNull { normalizeJson(it) }

    val useful = allCandidates.filter { candidate ->
        hasExamAnswerJson(candidate) || extractClassScoreReport(candidate) != null
    }

    if (useful.isEmpty() && allCandidates.isEmpty()) return null

    val selected = useful.ifEmpty { allCandidates }
    if (selected.size == 1 && selected.first() is JSONObject) {
        return selected.first()
    }

    val root = JSONObject()
    val usefulArray = JSONArray()
    useful.take(20).forEach { usefulArray.put(it) }
    val candidatesArray = JSONArray()
    allCandidates.take(40).forEach { candidatesArray.put(it) }
    root.put("useful_candidates", usefulArray)
    root.put("candidates", candidatesArray)

    selected.firstOrNull { it is JSONObject }?.let { firstObj ->
        (firstObj as JSONObject).keys().forEach { key ->
            if (!root.has(key)) root.put(key, firstObj.get(key))
        }
    }
    return root
}

private fun extractJsonParseCandidates(text: String): List<Any> {
    val pattern = Regex("""JSON\.parse\(\s*(['"])(.*?)\1\s*\)""", RegexOption.DOT_MATCHES_ALL)
    return pattern.findAll(text).mapNotNull { match ->
        val raw = match.groupValues[2]
        listOf(raw, decodeUnicodeEscapes(raw))
            .asSequence()
            .mapNotNull { parseJsonCandidate(it) }
            .firstOrNull()
    }.toList()
}

private fun extractRawJsonCandidates(text: String, maxCandidates: Int): List<Any> {
    val candidates = mutableListOf<Any>()
    text.indices.asSequence()
        .filter { text[it] == '{' || text[it] == '[' }
        .forEach { start ->
            val opening = text[start]
            val closing = if (opening == '{') '}' else ']'
            extractBalancedJsonAt(text, start, opening, closing)?.let { candidate ->
                candidates += candidate
                if (candidates.size >= maxCandidates) return candidates
            }
        }
    return candidates
}

private fun extractBalancedJsonAt(text: String, start: Int, opening: Char, closing: Char): Any? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val char = text[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            opening -> depth += 1
            closing -> {
                depth -= 1
                if (depth == 0) {
                    return parseJsonCandidate(text.substring(start, index + 1))
                }
            }
        }
    }
    return null
}

private fun hasExamAnswerJson(value: Any?): Boolean {
    return when (val normalized = normalizeJson(value)) {
        is JSONArray -> (0 until normalized.length()).any { index -> hasExamAnswerJson(normalized.opt(index)) }
        is JSONObject -> {
            if (normalized.has("model_info") || normalized.has("model_score_infos")) return true
            if (STANDARD_ANSWER_KEYS.any { normalized.has(it) }) return true
            normalized.keys().asSequence().any { key -> hasExamAnswerJson(normalized.opt(key)) }
        }
        is String -> {
            val lowered = normalized.lowercase()
            listOf("model_info", "model_score_infos", "answer", "right_answer", "standard_answer").any { it in lowered }
        }
        else -> false
    }
}

private fun extractBalancedJson(text: String, opening: Char): Any? {
    val closing = if (opening == '{') '}' else ']'
    text.indices.asSequence()
        .filter { text[it] == opening }
        .forEach { start ->
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until text.length) {
                val char = text[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                    continue
                }
                when (char) {
                    '"' -> inString = true
                    opening -> depth += 1
                    closing -> {
                        depth -= 1
                        if (depth == 0) {
                            parseJsonCandidate(text.substring(start, index + 1))?.let { return it }
                            break
                        }
                    }
                }
            }
        }
    return null
}

private fun parseJsonCandidate(text: String): Any? {
    val candidate = text.trim()
    return when {
        candidate.startsWith("{") -> runCatching { JSONObject(candidate) }.getOrNull()
        candidate.startsWith("[") -> runCatching { JSONArray(candidate) }.getOrNull()
        else -> null
    }
}

private fun decodeHtmlEntities(value: String): String {
    return value
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#x22;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&#xA0;", " ")
        .replace("&#xa0;", " ")
}

private fun decodeUnicodeEscapes(value: String): String {
    val pattern = Regex("""\\u([0-9a-fA-F]{4})""")
    return pattern.replace(value) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
}

private fun cleanDisplayText(value: String): String {
    return decodeUnicodeEscapes(decodeHtmlEntities(repairMojibakeText(value)))
        .replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
        .replace(Regex(""" *\n *"""), "\n")
        .trim()
}

internal data class EkwingScoreRequest(
    val path: String,
    val payload: Map<String, String>,
)

internal object EkwingHomeworkApiPlanner {
    fun homeworkListPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = HOMEWORK_LIST_PATH,
            basic = BASIC_HOMEWORK_LIST_PATH,
        )
    }

    fun studyCenterPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = STUDY_CENTER_PATH,
            basic = BASIC_STUDY_CENTER_PATH,
        )
    }

    fun examHistoryPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = EXAM_HISTORY_PATH,
            basic = BASIC_EXAM_HISTORY_PATH,
        )
    }

    fun examScorePaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = EXAM_SCORE_PATH,
            basic = BASIC_EXAM_SCORE_PATH,
        )
    }

    fun detailPaths(useBasic: Boolean): List<String> {
        return preferBasicPath(
            useBasic = useBasic,
            normal = HOMEWORK_ITEMS_PATH,
            basic = BASIC_HOMEWORK_ITEMS_PATH,
        ) + preferBasicPath(
            useBasic = useBasic,
            normal = SCORE_DETAIL_PATH,
            basic = BASIC_SCORE_DETAIL_PATH,
        )
    }

    private fun preferBasicPath(useBasic: Boolean, normal: String, basic: String): List<String> {
        return if (useBasic) listOf(basic, normal) else listOf(normal, basic)
    }
}

private fun <T> requestFirstSuccessful(
    paths: List<String>,
    request: (String) -> T,
): T {
    var lastError: Throwable? = null
    paths.distinct().forEach { path ->
        runCatching { request(path) }
            .onSuccess { return it }
            .onFailure { lastError = it }
    }
    throw lastError ?: RuntimeException("没有可用接口")
}

internal object EkwingHomeworkRequestPlanner {
    fun homeworkListRequest(
        history: Boolean,
        page: Int,
        commonParams: Map<String, String>,
        archiveId: String? = null,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["method"] = if (history) "finish" else "new"
        payload["page"] = page.toString()
        payload["sortMethod"] = "desc"
        payload["sortField"] = if (history) "finish_times" else "publish_times"
        if (!archiveId.isNullOrBlank()) {
            payload["archiveId"] = archiveId
        }
        return EkwingScoreRequest(path = "", payload = payload)
    }

    fun examHistoryRequest(
        page: Int,
        commonParams: Map<String, String>,
        archiveId: String? = null,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["type"] = "his"
        payload["page"] = page.toString()
        if (!archiveId.isNullOrBlank()) {
            payload["archiveId"] = archiveId
        }
        return EkwingScoreRequest(path = "", payload = payload)
    }

    fun examItemRequest(
        selfId: String,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["self_id"] = selfId
        return EkwingScoreRequest(path = EXAM_ITEM_PATH, payload = payload)
    }

    fun examScoreRequest(
        exam: JSONObject,
        selfId: String,
        useBasic: Boolean,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        val url = examScoreUrl(exam)
        val path = url?.pathFromUrl()
            ?: EkwingHomeworkApiPlanner.examScorePaths(useBasic).first()
        val payload = commonParams.toMutableMap()
        url?.let { rawUrl ->
            queryParams(rawUrl).forEach { (key, value) -> payload[key] = value }
        }
        payload["self_id"] = selfId
        payload["method"] = "exam_result"
        if ("type" !in payload && path.contains("basic", ignoreCase = true)) {
            payload["type"] = "0"
        }
        return EkwingScoreRequest(path = path, payload = payload)
    }

    fun detailItemsRequest(
        homework: JSONObject,
        path: String,
        page: Int,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        val hid = homeworkHid(homework) ?: throw RuntimeException("作业缺少 hid/id")
        val payload = commonParams.toMutableMap()
        payload["hid"] = hid
        payload["page"] = page.toString()
        payload["archiveId"] = homework.homeworkArchiveId()
        if (path == SCORE_DETAIL_PATH || path == BASIC_SCORE_DETAIL_PATH) {
            payload["self_id"] = hid
            payload["is_exercise"] = "0"
        }
        return EkwingScoreRequest(path = path, payload = payload)
    }

    fun contentRequest(
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
    ): EkwingScoreRequest {
        if (homeworkItemContentId(item) == null) {
            throw RuntimeException("作业小项缺少 hwcid/id")
        }
        return itemRequest(
            path = HW_DO_ITEM_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf(
                "method" to "last",
                "is_exercise" to "0",
            ),
        )
    }

    fun answerRequests(
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
    ): List<EkwingScoreRequest> {
        val requests = mutableListOf<EkwingScoreRequest>()
        if (homeworkItemContentId(item) != null) {
            requests += itemRequest(
                path = HW_ANSWER_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("method" to "LAST"),
            )
            requests += itemRequest(
                path = HW_ANSWER_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("method" to "MAX"),
            )
            requests += itemRequest(
                path = HW_COUNT_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0"),
            )
            requests += itemRequest(
                path = HW_HISTORY_SCORE_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0", "method" to "last"),
            )
            requests += itemRequest(
                path = HW_HISTORY_SCORE_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0"),
            )
            requests += itemRequest(
                path = HW_RESULT_PATH,
                homework = homework,
                item = item,
                commonParams = commonParams,
                extra = mapOf("is_exercise" to "0"),
            )
        }
        trainingAnswerRequest(
            path = TRAIN_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "LAST"),
        )?.let { requests += it }
        trainingAnswerRequest(
            path = TRAIN_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "MAX"),
        )?.let { requests += it }
        trainingAnswerRequest(
            path = TRAIN_JS_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "LAST"),
        )?.let { requests += it }
        trainingAnswerRequest(
            path = TRAIN_JS_ITEM_ANSWER_PATH,
            homework = homework,
            item = item,
            commonParams = commonParams,
            extra = mapOf("method" to "MAX"),
        )?.let { requests += it }
        return requests
    }

    private fun trainingAnswerRequest(
        path: String,
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
        extra: Map<String, String>,
    ): EkwingScoreRequest? {
        val unitId = item.optString("unit_id").takeIf { it.isNotBlank() } ?: return null
        val type = item.optString("type").takeIf { it.isNotBlank() } ?: return null
        val recordId = item.optString("record_id").takeIf { it.isNotBlank() } ?: return null
        val payload = commonParams.toMutableMap()
        payload["unit_id"] = unitId
        payload["type"] = type
        payload["record_id"] = recordId
        payload["archiveId"] = homework.homeworkArchiveId()
        payload.putAll(extra)
        return EkwingScoreRequest(path = path, payload = payload)
    }

    private fun itemRequest(
        path: String,
        homework: JSONObject,
        item: JSONObject,
        commonParams: Map<String, String>,
        extra: Map<String, String>,
    ): EkwingScoreRequest {
        val payload = commonParams.toMutableMap()
        payload["hid"] = homeworkItemHid(homework, item).orEmpty()
        payload["hwcid"] = homeworkItemContentId(item).orEmpty()
        payload["archiveId"] = homework.homeworkArchiveId()
        payload.putAll(extra)
        return EkwingScoreRequest(path = path, payload = payload)
    }
}

private fun repairMojibakeText(value: String): String {
    val repaired = runCatching {
        value.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
    }.getOrNull() ?: return value
    return if (countCjk(repaired) > countCjk(value)) repaired else value
}

private fun countCjk(value: String): Int {
    return value.count { it in '\u4e00'..'\u9fff' }
}


private fun firstAnswerValue(item: JSONObject): Any? {
    return PRIMARY_ANSWER_KEYS.firstNotNullOfOrNull { key ->
        if (item.has(key) && item.opt(key) != JSONObject.NULL) item.opt(key) else null
    }
}

private fun firstQuestionText(item: JSONObject): String? {
    return firstNonBlank(*QUESTION_KEYS.map { key -> item.optString(key) }.toTypedArray())
        ?.let(::cleanDisplayText)
        ?.takeIf { it.isNotBlank() }
}

private val PRIMARY_ANSWER_KEYS = listOf(
    "answer",
    "answers",
    "ans",
    "right_answer",
    "rightAnswer",
    "standard_answer",
    "standardAnswer",
    "correct_answer",
    "correctAnswer",
    "std_answer",
    "real_answer",
    "reference_answer",
    "refText",
    "real_text",
    "dis_text",
    "right",
    "right_ans",
    "rightAns",
    "rightText",
    "right_text",
    "option_answer",
    "correctText",
    "correct_text",
    "stdAnswer",
    "ref_answer",
)

private val STANDARD_ANSWER_KEYS = PRIMARY_ANSWER_KEYS

private val QUESTION_KEYS = listOf(
    "title_text",
    "title",
    "question",
    "question_text",
    "ques_title",
    "q_title",
    "stem",
    "text",
    "txt",
    "word",
    "sentence",
    "content",
    "topic",
    "body",
    "prompt",
    "material",
)

private val ANSWER_TEXT_KEYS = listOf(
    "text",
    "txt",
    "word",
    "sentence",
    "content",
    "content_text",
    "contentText",
    "display_text",
    "displayText",
    "value",
    "name",
    "label",
    "result",
    "result_text",
    "resultText",
    "option",
    "option_text",
    "optionText",
)

private val DETAIL_LIST_KEYS = listOf(
    "data",
    "list",
    "items",
    "rows",
    "records",
    "result",
    "detail",
    "details",
    "hw_items",
    "hwItems",
)

private val DETAIL_PAGE_KEYS = listOf(
    "currentPage",
    "totalPage",
    "current_page",
    "total_page",
    "page",
    "pageNum",
    "pageNo",
    "pageIndex",
    "current",
    "total",
    "pageCount",
    "pages",
    "lastPage",
)

private val DETAIL_CURRENT_PAGE_KEYS = listOf(
    "currentPage",
    "current_page",
    "page",
    "pageNum",
    "pageNo",
    "pageIndex",
    "current",
)

private val DETAIL_TOTAL_PAGE_KEYS = listOf(
    "totalPage",
    "total_page",
    "pageCount",
    "pages",
    "lastPage",
    "total",
)

private val DETAIL_ITEM_HINT_KEYS = listOf(
    "hid",
    "hw_id",
    "unit_id",
    "record_id",
    "type",
    "type_name",
    "tk_biz",
    "url",
    "num",
)

private fun JSONObject.toAnswerPaper(index: Int): EkwingAnswerPaper {
    val title = firstNonBlank(optString("title"), optString("self_title"), "考试 ${index + 1}") ?: "考试 ${index + 1}"
    val count = firstNonBlank(optString("cntTotal"), optString("finishCntNum"), optString("num"), optString("ques_num"))
    val score = firstNonBlank(optString("score"), optString("self_score"))
    val status = firstNonBlank(optString("status"), optString("self_status"))
    val summaryParts = listOfNotNull(
        "学习中心考试",
        count?.let { "$it 题" },
        score?.let { "分数 $it" },
        status?.let { "状态 ${examStatusText(it)}" },
    )
    return EkwingAnswerPaper(
        key = answerPaperKey(),
        title = title,
        summary = summaryParts.joinToString(" | ").ifBlank { "学习中心考试" },
    )
}

internal fun JSONObject.toHomeworkAnswerPaper(index: Int): EkwingAnswerPaper {
    val title = firstNonBlank(
        optString("title"),
        optString("hw_name"),
        optString("name"),
        "作业 ${index + 1}",
    ) ?: "作业 ${index + 1}"
    val total = firstNonBlank(
        optString("cntTotal"),
        optString("total_num"),
        optString("question_num"),
        optString("ques_num"),
        optString("num"),
    )
    val finished = firstNonBlank(
        optString("finishCntNum"),
        optString("finish_num"),
        optString("finished_num"),
        optString("done_num"),
    )
    val score = firstNonBlank(optString("score"), optString("self_score"), optString("total_score"))
    val status = firstNonBlank(optString("status"), optString("self_status"), optString("state"))
    val summaryParts = listOfNotNull(
        "常规作业",
        total?.let { "$it 题" },
        when {
            !finished.isNullOrBlank() && !total.isNullOrBlank() -> "完成 $finished/$total"
            !finished.isNullOrBlank() -> "完成 $finished"
            else -> null
        },
        score?.let { "分数 $it" },
        status?.let { "状态 ${homeworkStatusText(it)}" },
    )
    return EkwingAnswerPaper(
        key = homeworkAnswerPaperKey(),
        title = title,
        summary = summaryParts.joinToString(" | ").ifBlank { "常规作业" },
    )
}

internal fun examStatusText(status: String): String {
    return when (status.trim()) {
        "1" -> "已完成"
        "2" -> "未完成"
        else -> status
    }
}

private fun JSONObject.answerPaperKey(): String {
    val type = optString("type").takeIf { it.isNotBlank() } ?: "task"
    val id = firstNonBlank(
        examSelfId(this),
        optString("record_id"),
        optString("self_id"),
        optString("hid"),
        optString("id"),
        optString("title"),
    ) ?: toString()
    return "$type:$id"
}

private fun JSONObject.homeworkAnswerPaperKey(): String {
    val id = firstNonBlank(
        optString("hid"),
        optString("hw_id"),
        optString("id"),
        optString("record_id"),
        firstQueryParam("hid", "hw_id", "id"),
        optString("title"),
    ) ?: toString()
    return "hw:$id"
}

private fun homeworkStatusText(status: String): String {
    return when (status.trim()) {
        "0" -> "未完成"
        "1" -> "已完成"
        "2" -> "未完成"
        else -> status
    }
}

private fun examSelfId(exam: JSONObject): String? {
    return firstNonBlank(
        exam.optString("self_id"),
        exam.firstQueryParam("self_id"),
        exam.optString("record_id"),
        exam.optString("id"),
    )
}

private fun examScoreUrl(exam: JSONObject): String? {
    return listOf(exam.optString("url"), exam.optString("start_url"))
        .firstOrNull { it.pathFromUrl()?.contains("scoreinfo", ignoreCase = true) == true }
}

internal fun extractModelScoreRequests(value: Any?): List<JSONObject> {
    val requests = linkedMapOf<String, JSONObject>()
    fun visit(item: Any?) {
        when (val normalized = normalizeJson(item)) {
            is JSONArray -> {
                for (index in 0 until normalized.length()) visit(normalized.opt(index))
            }
            is JSONObject -> {
                val url = normalized.optString("url")
                val path = url.pathFromUrl()
                if (path == GET_MODEL_SCORE_PATH) {
                    val modelId = firstNonBlank(normalized.optString("model_id"), url.queryValue("model_id"))
                    val selfId = firstNonBlank(normalized.optString("self_id"), url.queryValue("self_id"))
                    val key = "${selfId.orEmpty()}:${modelId.orEmpty()}:$url"
                    requests[key] = JSONObject().apply {
                        put("url", url)
                        put("path", path)
                        put("self_id", selfId.orEmpty())
                        put("model_id", modelId.orEmpty())
                    }
                }
                normalized.keys().forEach { key -> visit(normalized.opt(key)) }
            }
        }
    }
    visit(value)
    return requests.values.toList()
}

private fun homeworkHid(homework: JSONObject): String? {
    return firstNonBlank(
        homework.optString("hid"),
        homework.optString("hw_id"),
        homework.optString("id"),
        homework.firstQueryParam("hid", "hw_id", "id"),
    )
}

private fun homeworkItemHid(homework: JSONObject, item: JSONObject): String? {
    return firstNonBlank(
        item.optString("hid"),
        item.optString("hw_id"),
        item.firstQueryParam("hid", "hw_id"),
        homeworkHid(homework),
    )
}

private fun homeworkItemContentId(item: JSONObject): String? {
    return firstNonBlank(
        item.optString("hwcid"),
        item.optString("hw_cnt_id"),
        item.optString("hwcnt_id"),
        item.optString("id"),
        item.firstQueryParam("hwcid", "hw_cnt_id", "hwcnt_id", "id"),
    )
}

private fun JSONObject.homeworkArchiveId(): String {
    return firstNonBlank(
        optString("archiveId"),
        optString("archive_id"),
        firstQueryParam("archiveId", "archive_id"),
    ).orEmpty()
}

private fun lastArchiveId(items: List<JSONObject>): String? {
    return items.asReversed().firstNotNullOfOrNull { item ->
        firstNonBlank(
            item.optString("archiveId"),
            item.optString("archive_id"),
            item.firstQueryParam("archiveId", "archive_id"),
        )
    }
}

private fun JSONObject.firstQueryParam(vararg names: String): String? {
    val urls = listOf(optString("url"), optString("start_url"))
    return urls.firstNotNullOfOrNull { url ->
        queryParams(url).firstNotNullOfOrNull { (key, value) ->
            value.takeIf { key in names && it.isNotBlank() }
        }
    }
}

private fun String.pathFromUrl(): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val pathStart = withoutScheme.indexOf('/')
    if (pathStart < 0) return null
    return withoutScheme.substring(pathStart).substringBefore("?").substringBefore("#")
}

private fun String.queryValue(name: String): String? {
    return queryParams(this).firstOrNull { (key, value) -> key == name && value.isNotBlank() }?.second
}

private fun JSONArray?.jsonObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.withQueryDefaults(defaults: Map<String, String>): String {
    val separator = if (contains("?")) "&" else "?"
    val existingKeys = substringAfter("?", "")
        .split("&")
        .mapNotNull { it.substringBefore("=", "").takeIf(String::isNotBlank) }
        .toSet()
    val extraQuery = defaults
        .filterKeys { it !in existingKeys }
        .entries
        .joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" }
    if (extraQuery.isBlank()) return this
    return this + separator + extraQuery
}

private fun String.absoluteEkwingUrl(): String {
    if (startsWith("http://") || startsWith("https://")) return this
    return EK_BASE_URL + if (startsWith("/")) this else "/$this"
}

private fun queryParams(url: String): List<Pair<String, String>> {
    val query = url.substringAfter("?", "").substringBefore("#")
    if (query.isBlank()) return emptyList()
    return query.split("&")
        .mapNotNull { part ->
            val key = part.substringBefore("=", "")
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter("=", "")
            key.urlDecode() to value.urlDecode()
        }
}

private fun String.urlDecode(): String {
    return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
}

private fun readText(input: InputStream): String {
    return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader -> reader.readText() }
}

private fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }
}
