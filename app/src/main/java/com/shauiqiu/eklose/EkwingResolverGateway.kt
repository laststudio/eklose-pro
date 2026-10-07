package com.shauiqiu.eklose

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * libekwing_resolver.so 的解析网关：把 EkwingAnswerReader 的答案解析切到
 * so 的 resolveRequest 契约入口（解析口径的单一事实源在 ekwing_resolver 仓库）。
 *
 * 约定：so 调用失败（未配置授权 / 网络拒签 / 形态变化）一律返回 null，
 * 由调用方回退到原有 Kotlin 解析逻辑，保证行为只增不减。
 * 解析类 op 在 Android 目标上由 so 契约层强制授权（answer_parser 开关式鉴权）。
 */
internal object EkwingResolverGateway {
    private const val TAG = "EkwingResolverGateway"

    /** 统一契约调用：返回响应信封，失败返回 null。阻塞调用，必须在 Dispatchers.IO 上。 */
    fun callOp(op: String, payload: JSONObject): JSONObject? {
        return runCatching {
            EkwingResolverBootstrap.ensureConfigured()
            val request = JSONObject().apply {
                put("version", 1)
                put("op", op)
                put("payload", payload)
            }
            val response = JSONObject(EkwingResolverNative.resolveRequest(request.toString()))
            if (response.optBoolean("ok", false)) response else null
        }.getOrElse { error ->
            Log.w(TAG, "so op=$op 调用失败: ${error.javaClass.simpleName}: ${error.message}")
            null
        }
    }

    /**
     * 模块成绩响应（getmodelscoreinfo 结果数组）-> 逐题标准答案。
     * 对齐 Python/Rust 口径：answer -> 朗读原文 -> 全字段兜底扫描。
     */
    fun examQuestions(modelScoreInfos: Any?): List<EkwingAnswerQuestion>? {
        val envelope = callOp("parse_exam_answers", JSONObject().apply {
            put("model_scores", wrapAny(modelScoreInfos))
        }) ?: return null
        val rows = envelope.optJSONArray("result") ?: return null
        return rows.toQuestions().takeIf { list -> list.isNotEmpty() }
    }

    /**
     * 模块成绩响应 -> 按大题（model_index）分节的答案表。
     * 朗读类模块（整模块一道题且题干==标准答案）保持原 UI 口径：
     * originalText=朗读原文，题目显示「朗读文本」。
     */
    fun examModelSections(modelScoreInfos: Any?): List<EkwingAnswerSection>? {
        val envelope = callOp("parse_exam_answers", JSONObject().apply {
            put("model_scores", wrapAny(modelScoreInfos))
        }) ?: return null
        val rows = envelope.optJSONArray("result") ?: return null
        if (rows.length() == 0) return null

        data class ModelRows(val name: String?, val type: String?, val rows: MutableList<JSONObject>)

        val grouped = linkedMapOf<Int, ModelRows>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val modelIndex = if (row.has("model_index") && !row.isNull("model_index")) {
                row.optInt("model_index", grouped.size + 1)
            } else {
                grouped.size + 1
            }
            val bucket = grouped.getOrPut(modelIndex) {
                ModelRows(
                    name = row.optStringOrNull("model_name"),
                    type = row.optStringOrNull("model_type"),
                    rows = mutableListOf(),
                )
            }
            bucket.rows += row
        }
        if (grouped.isEmpty()) return null

        val sections = grouped.entries.mapIndexed { groupIndex, (_, model) ->
            val title = model.name ?: "模块 ${groupIndex + 1}"
            val questions = model.rows.map { row ->
                row.optStringOrNull("question").orEmpty() to
                    row.optJSONArray("answers").orEmpty().textLines()
            }
            val isReadingModel = questions.size == 1 &&
                questions[0].first.isNotBlank() &&
                questions[0].first == questions[0].second
            EkwingAnswerSection(
                title = title,
                category = model.type ?: "exam",
                originalText = if (isReadingModel) questions[0].second else questions.firstOrNull()?.first ?: title,
                questions = if (isReadingModel) {
                    listOf(EkwingAnswerQuestion(order = "Q1", question = "朗读文本", answer = questions[0].second))
                } else {
                    questions.mapIndexed { questionIndex, (text, answer) ->
                        EkwingAnswerQuestion(
                            order = "Q${questionIndex + 1}",
                            question = text.ifBlank { "题目 ${questionIndex + 1}" },
                            answer = answer.ifBlank { "未解析到标准答案" },
                        )
                    }
                },
            )
        }
        return sections.ifEmpty { null }
    }

    /**
     * 作业/训练答案接口 data -> 逐题作答记录。
     * 兼容 getHwAns（data.ans 字符串外壳）、gethwcnt（word_list）、getitemans 外壳。
     */
    fun homeworkQuestions(answerData: Any?): List<EkwingAnswerQuestion>? {
        val envelope = callOp("parse_homework_answers", JSONObject().apply {
            put("data", wrapAny(answerData))
            put("source", "getHwAns")
        }) ?: return null
        val rows = envelope.optJSONArray("result") ?: return null
        return rows.toQuestions().takeIf { list -> list.isNotEmpty() }
    }

    /**
     * 成绩页（JSON 或 HTML 模式）-> 按大题分节的答案表。
     * so 侧等价于 parse_score_report + parse_answer_sheet 的合并口径
     * （成绩页与模块分两条路径认题去重、朗读类归 __single__）。
     */
    fun scorePageSections(scoreInfo: Any?): List<EkwingAnswerSection>? {
        val structured = callOp("parse_score_report", JSONObject().apply {
            put("score_data", wrapAny(scoreInfo))
        }) ?: return null
        val report = structured.optJSONObject("result") ?: return null
        if (!report.optBoolean("found", false)) return null

        val sheet = callOp("parse_answer_sheet", JSONObject().apply {
            put("structured_report", report)
            put("answers_by_question", JSONArray())
            put("score_globals", wrapAny(scoreInfo))
        }) ?: return null
        val rows = sheet.optJSONObject("result")?.optJSONArray("questions") ?: return null
        if (rows.length() == 0) return null

        val sections = mutableListOf<EkwingAnswerSection>()
        var currentTitle: String? = null
        var currentQuestions = mutableListOf<EkwingAnswerQuestion>()
        fun flush() {
            if (currentQuestions.isNotEmpty()) {
                sections += EkwingAnswerSection(
                    title = currentTitle ?: "答案",
                    category = "exam",
                    originalText = currentTitle ?: "已读取考试成绩",
                    questions = currentQuestions.toList(),
                )
            }
            currentQuestions = mutableListOf()
        }
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val title = row.optStringOrNull("section")
                ?: row.optStringOrNull("model_name")
                ?: "答案"
            if (title != currentTitle) {
                flush()
                currentTitle = title
            }
            val answers = row.optJSONArray("standard_answer").orEmpty().textLines()
            val questionText = row.optStringOrNull("question")
                ?: row.optStringOrNull("question_id")
                ?: "题目 ${index + 1}"
            currentQuestions += EkwingAnswerQuestion(
                order = "Q${currentQuestions.size + 1}",
                question = questionText,
                answer = answers,
            )
        }
        flush()
        return sections.ifEmpty { null }
    }

    // ============== so 输出 -> Kotlin 模型 ==============

    private fun JSONArray.toQuestions(): List<EkwingAnswerQuestion> {
        val questions = mutableListOf<EkwingAnswerQuestion>()
        for (index in 0 until length()) {
            val row = optJSONObject(index) ?: continue
            val answers = row.optJSONArray("answers").orEmpty().textLines()
                .ifBlank {
                    // 作业答案行的标准答案是单值字段（right/realText/...）
                    row.opt("standard_answer")?.displayValue().orEmpty()
                }
            questions += EkwingAnswerQuestion(
                order = "Q${questions.size + 1}",
                question = row.optStringOrNull("question").orEmpty(),
                answer = answers,
            )
        }
        return questions
        // so 口径：空 question 的行（大题直接挂答案的模块级明细）在
        // scorePageSections 的分节里用 question_id 兜底，这里保持原样。
    }

    private fun JSONArray.textLines(): String {
        val lines = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = opt(index) ?: continue
            if (value == JSONObject.NULL) continue
            val text = value.toString()
            if (text.isNotBlank()) lines += text
        }
        return lines.joinToString("\n")
    }

    private fun Any?.displayValue(): String? {
        if (this == null || this == JSONObject.NULL) return null
        return when (this) {
            is JSONArray -> textLines().ifBlank { null }
            is JSONObject -> null
            is String -> ifBlank { null }
            else -> toString().ifBlank { null }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val value = opt(key) ?: return null
        if (value == JSONObject.NULL) return null
        val text = value.toString()
        return text.ifBlank { null }
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    /** 把 Any?（JSONObject/JSONArray/字符串形态的 JSON/其他）装进 payload 可接受形态。 */
    private fun wrapAny(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray -> value
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    runCatching { JSONObject(trimmed) }.getOrElse {
                        runCatching { JSONArray(trimmed) }.getOrElse { value }
                    }
                } else {
                    value
                }
            }
            else -> JSONObject.wrap(value) ?: value.toString()
        }
    }
}
