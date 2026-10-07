package com.shauiqiu.eklose

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EkwingAnswerReaderParserTest {
    @Test
    fun extractModelScoreRequestsMatchesReleaseScoreInfoLinks() {
        val requests = extractModelScoreRequests(
            JSONObject(
                """
                {
                  "ans_info": {
                    "content": [
                      {
                        "ques_list": [
                          {
                            "url": "https://mapi.ekwing.com/student/exam/getmodelscoreinfo?self_id=self-1&model_id=model-7",
                            "status": "done"
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, requests.size)
        assertEquals("/student/exam/getmodelscoreinfo", requests.single().optString("path"))
        assertEquals("self-1", requests.single().optString("self_id"))
        assertEquals("model-7", requests.single().optString("model_id"))
    }

    @Test
    fun studyCenterTasksAcceptObjectWrappedLists() {
        val tasks = EkwingTaskListParser.studyCenterExamTasks(
            JSONObject(
                """
                {
                  "list": [
                    {"type": "exam", "id": "exam-1"},
                    {"type": "hw", "id": "hw-1"},
                    {"type": "train", "id": "train-1"},
                    {"type": "notice", "id": "notice-1"}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("exam-1"), tasks.map { it.optString("id") })
    }

    @Test
    fun studyCenterTasksAcceptCaseInsensitiveExamType() {
        val tasks = EkwingTaskListParser.studyCenterExamTasks(
            JSONObject(
                """
                {
                  "list": [
                    {"type": " EXAM ", "id": "exam-upper"},
                    {"type": "hw", "id": "hw-1"}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("exam-upper"), tasks.map { it.optString("id") })
    }

    @Test
    fun studyCenterTasksCollectNestedArrays() {
        val tasks = EkwingTaskListParser.studyCenterExamTasks(
            JSONObject(
                """
                {
                  "exam": [
                    {"type": "exam", "id": "exam-1"},
                    {"type": "exam", "id": "exam-2"}
                  ],
                  "groups": {
                    "homework": [
                      {"type": "hw", "id": "hw-1"},
                      {"type": "hw", "id": "hw-2"}
                    ],
                    "train": [
                      {"type": "train", "id": "train-1"}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("exam-1", "exam-2"), tasks.map { it.optString("id") })
    }

    @Test
    fun homeworkListTasksKeepCurrentAndFinishedHomeworkItems() {
        val tasks = EkwingTaskListParser.homeworkListTasks(
            JSONObject(
                """
                {
                  "list": [
                    {"hid": "current-1", "title": "Current homework"},
                    {"hid": "finished-1", "title": "Finished homework", "type": "anything"}
                  ],
                  "page": {"currentPage": 1, "totalPage": 1}
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("current-1", "finished-1"), tasks.map { it.optString("hid") })
    }

    @Test
    fun answerAssemblerKeepsOtherHomeworksWhenOneFails() {
        val homeworks = listOf(
            JSONObject("""{"type":"hw","id":"ok-1","title":"可读取作业"}"""),
            JSONObject("""{"type":"hw","id":"fail-1","title":"失败作业"}"""),
        )
        val result = EkwingAnswerAssembler.buildAnswerState(homeworks) { homework ->
            if (homework.optString("id") == "fail-1") {
                error("详情接口失败")
            }
            listOf(
                EkwingAnswerSection(
                    title = "答案",
                    category = "answer",
                    originalText = "已读取",
                    questions = listOf(
                        EkwingAnswerQuestion(
                            order = "Q1",
                            question = "题目",
                            answer = "答案",
                        )
                    ),
                )
            )
        }

        assertEquals(listOf("可读取作业", "失败作业"), result.papers.map { it.title })
        val okSections = result.sectionsByPaperKey.getValue("hw:ok-1")
        val failedSections = result.sectionsByPaperKey.getValue("hw:fail-1")
        assertEquals("答案", okSections.single().title)
        assertEquals("读取失败", failedSections.single().title)
        assertEquals("error", failedSections.single().category)
        assertEquals("详情接口失败", failedSections.single().questions.single().answer)
    }

    @Test
    fun answerStateCanKeepExamListWithoutEagerSections() {
        val exam = JSONObject("""{"type":"exam","self_id":"exam-100","title":"当前考试"}""")
        val paper = exam.toAnswerPaperForTest(0)

        EkwingAnswerState.papers = listOf(paper)
        EkwingAnswerState.taskByPaperKey = mapOf(paper.key to exam.toString())
        EkwingAnswerState.sectionsByPaperKey = emptyMap()

        assertEquals(listOf("当前考试"), EkwingAnswerState.papers.map { it.title })
        assertEquals(exam.toString(), EkwingAnswerState.taskByPaperKey.getValue("exam:exam-100"))
        assertEquals(emptyMap<String, List<EkwingAnswerSection>>(), EkwingAnswerState.sectionsByPaperKey)
        EkwingAnswerState.clear()
    }

    @Test
    fun examStatusTextDisplaysKnownStatusCodes() {
        assertEquals("已完成", examStatusText("1"))
        assertEquals("未完成", examStatusText("2"))
        assertEquals("3", examStatusText("3"))
    }

    @Test
    fun answerSourceStateSerializesPapersSectionsAndStatuses() {
        val state = EkwingAnswerSourceState(
            papers = listOf(EkwingAnswerPaper("exam:1", "考试 1", "学习中心考试")),
            taskByPaperKey = mapOf("exam:1" to """{"self_id":"1"}"""),
            sectionsByPaperKey = mapOf(
                "exam:1" to listOf(
                    EkwingAnswerSection(
                        title = "考试答案",
                        category = "exam",
                        originalText = "原文",
                        questions = listOf(
                            EkwingAnswerQuestion(
                                order = "Q1",
                                question = "题目",
                                answer = "答案",
                            )
                        ),
                    )
                )
            ),
            parseStateByPaperKey = mapOf(
                "exam:1" to EkwingAnswerParseState(EkwingAnswerParseStatus.Ready),
                "exam:2" to EkwingAnswerParseState(EkwingAnswerParseStatus.Failed, "失败"),
            ),
        )

        val restored = sourceStateFromJson(sourceStateToJson(state))

        assertEquals(state.papers, restored.papers)
        assertEquals(state.taskByPaperKey, restored.taskByPaperKey)
        assertEquals(state.sectionsByPaperKey, restored.sectionsByPaperKey)
        assertEquals(EkwingAnswerParseStatus.Ready, restored.parseStateByPaperKey.getValue("exam:1").status)
        assertEquals(EkwingAnswerParseStatus.Failed, restored.parseStateByPaperKey.getValue("exam:2").status)
        assertEquals("失败", restored.parseStateByPaperKey.getValue("exam:2").errorMessage)
    }

    @Test
    fun answerStateKeepsCurrentAndHistorySeparate() {
        EkwingAnswerState.clear()
        EkwingAnswerState.setSourceState(
            EkwingAnswerSource.Current,
            EkwingAnswerSourceState(papers = listOf(EkwingAnswerPaper("current", "当前", "summary"))),
        )
        EkwingAnswerState.setSourceState(
            EkwingAnswerSource.History,
            EkwingAnswerSourceState(papers = listOf(EkwingAnswerPaper("history", "历史", "summary"))),
        )

        assertEquals(listOf("当前"), EkwingAnswerState.sourceState(EkwingAnswerSource.Current).papers.map { it.title })
        assertEquals(listOf("历史"), EkwingAnswerState.sourceState(EkwingAnswerSource.History).papers.map { it.title })
        EkwingAnswerState.clear()
    }

    @Test
    fun answerStateKeepsAllSourcesSeparate() {
        EkwingAnswerState.clear()
        val expectedTitles = mapOf(
            EkwingAnswerSource.Current to "当前考试",
            EkwingAnswerSource.History to "历史考试",
            EkwingAnswerSource.HomeworkCurrent to "当前作业",
            EkwingAnswerSource.HomeworkHistory to "历史作业",
            EkwingAnswerSource.StudyCenter to "学习中心作业/训练",
        )

        expectedTitles.forEach { (source, title) ->
            EkwingAnswerState.setSourceState(
                source,
                EkwingAnswerSourceState(papers = listOf(EkwingAnswerPaper(source.cacheKey, title, "summary"))),
            )
        }

        assertEquals(
            expectedTitles,
            EkwingAnswerSource.entries.associateWith { source ->
                EkwingAnswerState.sourceState(source).papers.single().title
            },
        )
        EkwingAnswerState.clear()
    }

    @Test
    fun answerStateClearRemovesAllSources() {
        EkwingAnswerState.setSourceState(
            EkwingAnswerSource.Current,
            EkwingAnswerSourceState(papers = listOf(EkwingAnswerPaper("current", "当前", "summary"))),
        )
        EkwingAnswerState.setSourceState(
            EkwingAnswerSource.History,
            EkwingAnswerSourceState(papers = listOf(EkwingAnswerPaper("history", "历史", "summary"))),
        )

        EkwingAnswerState.clear()

        assertEquals(emptyList<EkwingAnswerPaper>(), EkwingAnswerState.sourceState(EkwingAnswerSource.Current).papers)
        assertEquals(emptyList<EkwingAnswerPaper>(), EkwingAnswerState.sourceState(EkwingAnswerSource.History).papers)
        assertEquals(emptyList<EkwingAnswerPaper>(), EkwingAnswerState.sourceState(EkwingAnswerSource.HomeworkCurrent).papers)
        assertEquals(emptyList<EkwingAnswerPaper>(), EkwingAnswerState.sourceState(EkwingAnswerSource.HomeworkHistory).papers)
    }

    @Test
    fun onlyReadyParseStateCanOpenPaper() {
        assertEquals(false, canOpenPaper(null))
        assertEquals(false, canOpenPaper(EkwingAnswerParseState(EkwingAnswerParseStatus.NotStarted)))
        assertEquals(false, canOpenPaper(EkwingAnswerParseState(EkwingAnswerParseStatus.Pending)))
        assertEquals(false, canOpenPaper(EkwingAnswerParseState(EkwingAnswerParseStatus.Loading)))
        assertEquals(false, canOpenPaper(EkwingAnswerParseState(EkwingAnswerParseStatus.Failed, "失败")))
        assertEquals(true, canOpenPaper(EkwingAnswerParseState(EkwingAnswerParseStatus.Ready)))
    }

    @Test
    fun answerStateClearRemovesLazyHomeworkCache() {
        EkwingAnswerState.papers = listOf(EkwingAnswerPaper("key", "title", "summary"))
        EkwingAnswerState.taskByPaperKey = mapOf("key" to "{}")
        EkwingAnswerState.sectionsByPaperKey = mapOf(
            "key" to listOf(
                EkwingAnswerSection(
                    title = "答案",
                    category = "answer",
                    originalText = "题目",
                    questions = emptyList(),
                )
            )
        )

        EkwingAnswerState.clear()

        assertEquals(emptyList<EkwingAnswerPaper>(), EkwingAnswerState.papers)
        assertEquals(emptyMap<String, String>(), EkwingAnswerState.taskByPaperKey)
        assertEquals(emptyMap<String, List<EkwingAnswerSection>>(), EkwingAnswerState.sectionsByPaperKey)
    }

    @Test
    fun reloginPlannerUsesSavedAccountCredentials() {
        val plan = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "account",
                username = " student001 ",
                name = null,
                schoolName = null,
                schoolId = null,
                password = "password123",
            )
        )

        assertEquals(EkwingReloginPlan.Account("student001", "password123"), plan)
    }

    @Test
    fun reloginPlannerUsesSavedRealNameCredentials() {
        val plan = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "real-name",
                username = null,
                name = " 张三 ",
                schoolName = " 第一中学 ",
                schoolId = " 1001 ",
                password = "password123",
            )
        )

        assertEquals(
            EkwingReloginPlan.RealName(
                name = "张三",
                password = "password123",
                schoolName = "第一中学",
                schoolId = "1001",
            ),
            plan,
        )
    }

    @Test
    fun reloginPlannerRequiresCompleteSavedCredentials() {
        val missingPassword = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "account",
                username = "student001",
                name = null,
                schoolName = null,
                schoolId = null,
                password = "",
            )
        )
        val missingSchool = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "real-name",
                username = null,
                name = "张三",
                schoolName = "第一中学",
                schoolId = "",
                password = "password123",
            )
        )

        assertEquals(EkwingReloginPlan.None, missingPassword)
        assertEquals(EkwingReloginPlan.None, missingSchool)
    }

    @Test
    fun reloginPlannerIgnoresUnknownLoginMethod() {
        val plan = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "unknown",
                username = "student001",
                name = "张三",
                schoolName = "第一中学",
                schoolId = "1001",
                password = "password123",
            )
        )

        assertEquals(EkwingReloginPlan.None, plan)
    }

    @Test
    fun sessionRefreshPlannerUsesRefreshedSessionWhenAvailable() {
        val current = EkwingLoginSession(
            uid = "old-uid",
            token = "old-token",
            userType = null,
        )
        val refreshed = EkwingLoginSession(
            uid = "new-uid",
            token = "new-token",
            userType = "student",
        )

        assertEquals(refreshed, EkwingSessionRefreshPlanner.actualSession(current, refreshed))
        assertEquals(current, EkwingSessionRefreshPlanner.actualSession(current, null))
    }

    @Test
    fun homeworkDetailParserAcceptsAlternateListKeysAndKeepsSameHidItems() {
        val items = EkwingHomeworkDetailParser.detailItems(
            JSONObject(
                """
                {
                  "data": {
                    "rows": [
                      {
                        "id": "item-1",
                        "hid": "homework-1",
                        "type_name": "朗读"
                      },
                      {
                        "id": "item-2",
                        "hid": "homework-1",
                        "type_name": "听选"
                      }
                    ]
                  },
                  "currentPage": 1,
                  "totalPage": 2
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("item-1", "item-2"), items.map { it.optString("id") })
    }

    @Test
    fun homeworkDetailParserAcceptsNestedDataContainer() {
        val items = EkwingHomeworkDetailParser.detailItems(
            JSONObject(
                """
                {
                  "data": {
                    "data": {
                      "list": [
                        {
                          "id": "nested-item",
                          "hid": "homework-1",
                          "tk_biz": "005"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("nested-item"), items.map { it.optString("id") })
    }

    @Test
    fun homeworkDetailParserReadsFlatPageFields() {
        val page = EkwingHomeworkDetailParser.pageInfo(
            JSONObject(
                """
                {
                  "items": [
                    {"id": "item-1", "hid": "homework-1"}
                  ],
                  "currentPage": 2,
                  "totalPage": 3
                }
                """.trimIndent()
            )
        )

        assertEquals(2, page?.optInt("currentPage"))
        assertEquals(3, page?.optInt("totalPage"))
    }

    @Test
    fun homeworkDetailParserNormalizesAlternatePageFields() {
        val page = EkwingHomeworkDetailParser.pageInfo(
            JSONObject(
                """
                {
                  "page": {
                    "pageNo": 4,
                    "pageCount": 6
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(4, page?.optInt("currentPage"))
        assertEquals(6, page?.optInt("totalPage"))
    }

    @Test
    fun homeworkDetailParserAcceptsNestedHwcidItemsWithoutId() {
        val items = EkwingHomeworkDetailParser.detailItems(
            JSONObject(
                """
                {
                  "data": {
                    "score": {
                      "rows": [
                        {
                          "hwcid": "course-300",
                          "hid": "item-hid-200",
                          "right_answer": "Answer from score detail"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("course-300"), items.map { it.optString("hwcid") })
    }

    @Test
    fun homeworkDetailItemKeyTreatsIdAndHwcidAsSameItem() {
        val fromItems = JSONObject(
            """
            {
              "id": "course-300",
              "hid": "item-hid-200",
              "url": "/student/Hw/item"
            }
            """.trimIndent()
        )
        val fromScoreDetail = JSONObject(
            """
            {
              "hwcid": "course-300",
              "hid": "item-hid-200",
              "right_answer": "Answer from score detail"
            }
            """.trimIndent()
        )

        assertEquals(
            EkwingHomeworkDetailParser.detailItemKey(fromItems),
            EkwingHomeworkDetailParser.detailItemKey(fromScoreDetail),
        )
    }

    @Test
    fun homeworkItemsRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.detailItemsRequest(
            homework = homeworkJson(),
            path = "/student/Hw/getHwItems",
            page = 2,
            commonParams = baseParams(),
        )

        assertEquals("/student/Hw/getHwItems", request.path)
        assertEquals("homework-100", request.payload["hid"])
        assertEquals("2", request.payload["page"])
        assertEquals("archive-9", request.payload["archiveId"])
        assertEquals("token-value", request.payload["token"])
        assertEquals(null, request.payload["self_id"])
        assertEquals(null, request.payload["is_exercise"])
    }

    @Test
    fun homeworkScoreDetailRequestAddsTrainingParams() {
        val request = EkwingHomeworkRequestPlanner.detailItemsRequest(
            homework = homeworkJson(),
            path = "/student/Hw/stuscoredetail",
            page = 1,
            commonParams = baseParams(),
        )

        assertEquals("/student/Hw/stuscoredetail", request.path)
        assertEquals("homework-100", request.payload["hid"])
        assertEquals("homework-100", request.payload["self_id"])
        assertEquals("0", request.payload["is_exercise"])
    }

    @Test
    fun homeworkDetailPathsTryItemsThenScoreDetail() {
        assertEquals(
            listOf(
                "/student/Hw/getHwItems",
                "/student/Hw/getBasicHwItems",
                "/student/Hw/stuscoredetail",
                "/student/Hw/stubasicscoredetail",
            ),
            EkwingHomeworkApiPlanner.detailPaths(useBasic = false),
        )
        assertEquals(
            listOf(
                "/student/Hw/getBasicHwItems",
                "/student/Hw/getHwItems",
                "/student/Hw/stubasicscoredetail",
                "/student/Hw/stuscoredetail",
            ),
            EkwingHomeworkApiPlanner.detailPaths(useBasic = true),
        )
    }

    @Test
    fun studyCenterPathsTryConfiguredAccountTypeThenFallback() {
        assertEquals(
            listOf("/student/Hw/getnewmainlist", "/student/Hw/getbasicnewmainlist"),
            EkwingHomeworkApiPlanner.studyCenterPaths(useBasic = false),
        )
        assertEquals(
            listOf("/student/Hw/getbasicnewmainlist", "/student/Hw/getnewmainlist"),
            EkwingHomeworkApiPlanner.studyCenterPaths(useBasic = true),
        )
    }

    @Test
    fun homeworkListPathsTryConfiguredAccountTypeThenFallback() {
        assertEquals(
            listOf("/student/Hw/getList", "/student/Hw/getBasicList"),
            EkwingHomeworkApiPlanner.homeworkListPaths(useBasic = false),
        )
        assertEquals(
            listOf("/student/Hw/getBasicList", "/student/Hw/getList"),
            EkwingHomeworkApiPlanner.homeworkListPaths(useBasic = true),
        )
    }

    @Test
    fun homeworkSourcesUseIndependentCacheKeysAndHistoryFlags() {
        assertEquals("current", EkwingAnswerSource.Current.cacheKey)
        assertEquals("history", EkwingAnswerSource.History.cacheKey)
        assertEquals("homework_current", EkwingAnswerSource.HomeworkCurrent.cacheKey)
        assertEquals("homework_history", EkwingAnswerSource.HomeworkHistory.cacheKey)
        assertEquals(false, EkwingAnswerSource.Current.isHomework)
        assertEquals(false, EkwingAnswerSource.History.isHomework)
        assertEquals(true, EkwingAnswerSource.HomeworkCurrent.isHomework)
        assertEquals(true, EkwingAnswerSource.HomeworkHistory.isHomework)
        assertEquals(false, EkwingAnswerSource.HomeworkCurrent.isHistory)
        assertEquals(true, EkwingAnswerSource.HomeworkHistory.isHistory)
    }

    @Test
    fun homeworkPaperUsesHidAndRegularHomeworkSummary() {
        val paper = JSONObject(
            """
            {
              "hid": "homework-100",
              "title": "暑假英语作业",
              "cntTotal": 10,
              "finishCntNum": 4,
              "score": 80,
              "status": 1
            }
            """.trimIndent()
        ).toHomeworkAnswerPaper(0)

        assertEquals("hw:homework-100", paper.key)
        assertEquals("暑假英语作业", paper.title)
        assertEquals("常规作业 | 10 题 | 完成 4/10 | 分数 80 | 状态 已完成", paper.summary)
    }

    @Test
    fun currentHomeworkListRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.homeworkListRequest(
            history = false,
            page = 1,
            commonParams = baseParams(),
        )

        assertEquals("new", request.payload["method"])
        assertEquals("1", request.payload["page"])
        assertEquals("desc", request.payload["sortMethod"])
        assertEquals("publish_times", request.payload["sortField"])
        assertEquals(null, request.payload["archiveId"])
        assertEquals("token-value", request.payload["token"])
    }

    @Test
    fun finishedHomeworkListRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.homeworkListRequest(
            history = true,
            page = 3,
            archiveId = "archive-last",
            commonParams = baseParams(),
        )

        assertEquals("finish", request.payload["method"])
        assertEquals("3", request.payload["page"])
        assertEquals("desc", request.payload["sortMethod"])
        assertEquals("finish_times", request.payload["sortField"])
        assertEquals("archive-last", request.payload["archiveId"])
    }

    @Test
    fun homeworkContentRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.contentRequest(
            homework = homeworkJson(),
            item = itemJson(),
            commonParams = baseParams(),
        )

        assertEquals("/student/Hw/hwdoitem", request.path)
        assertEquals("item-hid-200", request.payload["hid"])
        assertEquals("course-300", request.payload["hwcid"])
        assertEquals("archive-9", request.payload["archiveId"])
        assertEquals("last", request.payload["method"])
        assertEquals("0", request.payload["is_exercise"])
    }

    @Test
    fun homeworkContentRequestUsesAlternateItemIds() {
        val request = EkwingHomeworkRequestPlanner.contentRequest(
            homework = homeworkJson(),
            item = JSONObject(
                """
                {
                  "hw_id": "item-hw-201",
                  "hw_cnt_id": "course-301"
                }
                """.trimIndent()
            ),
            commonParams = baseParams(),
        )

        assertEquals("item-hw-201", request.payload["hid"])
        assertEquals("course-301", request.payload["hwcid"])
    }

    @Test
    fun homeworkContentRequestDoesNotUseRecordIdAsHwcid() {
        val itemWithoutHwcid = JSONObject(
            """
            {
              "hid": "item-hid-200",
              "unit_id": "unit-1",
              "type": "005",
              "record_id": "record-7"
            }
            """.trimIndent()
        )

        val contentError = assertThrows(RuntimeException::class.java) {
            EkwingHomeworkRequestPlanner.contentRequest(
                homework = homeworkJson(),
                item = itemWithoutHwcid,
                commonParams = baseParams(),
            )
        }
        assertEquals("作业小项缺少 hwcid/id", contentError.message)

        val answerRequests = EkwingHomeworkRequestPlanner.answerRequests(
            homework = homeworkJson(),
            item = itemWithoutHwcid,
            commonParams = baseParams(),
        )

        assertEquals(
            listOf(
                "/student/train/getitemans",
                "/student/train/getitemans",
                "/student/train/getjsitemans",
                "/student/train/getjsitemans",
            ),
            answerRequests.map { it.path },
        )
        assertEquals("record-7", answerRequests.last().payload["record_id"])
    }

    @Test
    fun homeworkRequestsUseIdsFromUrlsWhenFieldsAreMissing() {
        val request = EkwingHomeworkRequestPlanner.contentRequest(
            homework = JSONObject(
                """
                {
                  "url": "https://mapi.ekwing.com/student/Hw/start?hid=homework-from-url&archiveId=archive-from-url"
                }
                """.trimIndent()
            ),
            item = JSONObject(
                """
                {
                  "url": "https://mapi.ekwing.com/student/Hw/item?hid=item-hid-from-url&hwcid=course-from-url"
                }
                """.trimIndent()
            ),
            commonParams = baseParams(),
        )

        assertEquals("item-hid-from-url", request.payload["hid"])
        assertEquals("course-from-url", request.payload["hwcid"])
        assertEquals("archive-from-url", request.payload["archiveId"])
    }

    @Test
    fun homeworkAnswerRequestsMatchDemoPayloads() {
        val requests = EkwingHomeworkRequestPlanner.answerRequests(
            homework = homeworkJson(),
            item = itemJson(),
            commonParams = baseParams(),
        )

        assertEquals(
            listOf(
                "/student/Hw/getHwAns",
                "/student/Hw/getHwAns",
                "/student/Hw/gethwcnt",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/GetHwResult",
            ),
            requests.map { it.path },
        )

        val answerPayload = requests[0].payload
        assertEquals("LAST", answerPayload["method"])
        assertEquals(null, answerPayload["is_exercise"])

        val maxAnswerPayload = requests[1].payload
        assertEquals("MAX", maxAnswerPayload["method"])
        assertEquals(null, maxAnswerPayload["is_exercise"])

        val countPayload = requests[2].payload
        assertEquals("0", countPayload["is_exercise"])
        assertEquals(null, countPayload["method"])

        val historyPayload = requests[3].payload
        assertEquals("0", historyPayload["is_exercise"])
        assertEquals("last", historyPayload["method"])

        val historyWithoutMethodPayload = requests[4].payload
        assertEquals("0", historyWithoutMethodPayload["is_exercise"])
        assertEquals(null, historyWithoutMethodPayload["method"])

        val resultPayload = requests[5].payload
        assertEquals("0", resultPayload["is_exercise"])
        assertEquals(null, resultPayload["method"])

        requests.forEach { request ->
            assertEquals("item-hid-200", request.payload["hid"])
            assertEquals("course-300", request.payload["hwcid"])
            assertEquals("archive-9", request.payload["archiveId"])
            assertEquals("uid-value", request.payload["uid"])
        }
    }

    @Test
    fun homeworkAnswerRequestsIncludeTrainingFallbackWhenItemHasTrainingFields() {
        val requests = EkwingHomeworkRequestPlanner.answerRequests(
            homework = homeworkJson(),
            item = JSONObject(
                """
                {
                  "id": "course-300",
                  "hid": "item-hid-200",
                  "unit_id": "unit-1",
                  "type": "005",
                  "record_id": "record-7"
                }
                """.trimIndent()
            ),
            commonParams = baseParams(),
        )

        assertEquals(
            listOf(
                "/student/Hw/getHwAns",
                "/student/Hw/getHwAns",
                "/student/Hw/gethwcnt",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/GetHwResult",
                "/student/train/getitemans",
                "/student/train/getitemans",
                "/student/train/getjsitemans",
                "/student/train/getjsitemans",
            ),
            requests.map { it.path },
        )
        val trainingPayload = requests.last().payload
        assertEquals("unit-1", trainingPayload["unit_id"])
        assertEquals("005", trainingPayload["type"])
        assertEquals("record-7", trainingPayload["record_id"])
        assertEquals("archive-9", trainingPayload["archiveId"])
        assertEquals("MAX", trainingPayload["method"])
    }

    private fun baseParams(): Map<String, String> {
        return mapOf(
            "uid" to "uid-value",
            "author_id" to "uid-value",
            "token" to "token-value",
        )
    }

    private fun homeworkJson(): JSONObject {
        return JSONObject(
            """
            {
              "hid": "homework-100",
              "archiveId": "archive-9"
            }
            """.trimIndent()
        )
    }

    private fun itemJson(): JSONObject {
        return JSONObject(
            """
            {
              "id": "course-300",
              "hid": "item-hid-200"
            }
            """.trimIndent()
        )
    }

    private fun JSONObject.toAnswerPaperForTest(index: Int): EkwingAnswerPaper {
        val title = optString("title").ifBlank { "考试 ${index + 1}" }
        val id = optString("self_id").ifBlank { optString("id") }
        return EkwingAnswerPaper(
            key = "${optString("type").ifBlank { "task" }}:$id",
            title = title,
            summary = "学习中心考试",
        )
    }

    @Test
    fun extractClassScoreReportFromUsefulCandidates() {
        val scoreData = JSONObject(
            """
            {
              "useful_candidates": [
                {
                  "user_rank": 2,
                  "user_score": "95.5",
                  "classes": "八年级(3)班",
                  "self_ranks": [
                    {
                      "index": 1,
                      "real_name": "张小明",
                      "score": "98.0",
                      "is_self": 0,
                      "user_info": {
                        "nicename": "小明",
                        "classes": "八年级(3)班",
                        "student_no": "2024001"
                      }
                    },
                    {
                      "index": 2,
                      "real_name": "李华",
                      "score": "95.5",
                      "is_self": 1,
                      "user_info": {
                        "nicename": "李华",
                        "classes": "八年级(3)班",
                        "student_no": "2024002"
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val report = extractClassScoreReport(scoreData)
        assertNotNull(report)
        assertEquals(2, report?.userRank)
        assertEquals("95.5", report?.userScore)
        assertEquals("八年级(3)班", report?.className)
        assertEquals(2, report?.ranks?.size)

        val rank0 = report!!.ranks[0]
        assertEquals(1, rank0.index)
        assertEquals("张小明", rank0.name)
        assertEquals("98.0", rank0.score)
        assertFalse(rank0.isSelf)
        assertEquals("八年级(3)班", rank0.className)
        assertEquals("2024001", rank0.studentNo)

        val rank1 = report.ranks[1]
        assertEquals(2, rank1.index)
        assertEquals("李华", rank1.name)
        assertEquals("95.5", rank1.score)
        assertTrue(rank1.isSelf)
        assertEquals("八年级(3)班", rank1.className)
        assertEquals("2024002", rank1.studentNo)
    }

    @Test
    fun extractClassScoreReportWithNestedUserRankObject() {
        val scoreData = JSONObject(
            """
            {
              "user_rank": {
                "rank": 3,
                "score": "91.0",
                "classes": "初一(1)班"
              },
              "self_ranks": [
                {
                  "index": 3,
                  "score": "91.0",
                  "is_self": true,
                  "user_info": {
                    "nicename": "王小华",
                    "classes": "初一(1)班",
                    "student_no": "103"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val report = extractClassScoreReport(scoreData)
        assertNotNull(report)
        assertEquals(3, report?.userRank)
        assertEquals("91.0", report?.userScore)
        assertEquals("初一(1)班", report?.className)
        assertEquals(1, report?.ranks?.size)
        assertEquals("王小华", report!!.ranks[0].name)
        assertTrue(report.ranks[0].isSelf)
    }

    @Test
    fun sourceStateSerializationPreservesClassScoreReport() {
        val report = EkwingClassScoreReport(
            userRank = 1,
            userScore = "100",
            className = "高一(1)班",
            ranks = listOf(
                EkwingClassRankItem(
                    index = 1,
                    name = "学霸",
                    score = "100",
                    isSelf = true,
                    className = "高一(1)班",
                    studentNo = "001"
                )
            )
        )
        val originalState = EkwingAnswerSourceState(
            papers = listOf(EkwingAnswerPaper(key = "exam:123", title = "期末考试", summary = "测试")),
            classScoreReportByPaperKey = mapOf("exam:123" to report)
        )
        val json = sourceStateToJson(originalState)
        val restoredState = sourceStateFromJson(json)

        val restoredReport = restoredState.classScoreReportByPaperKey["exam:123"]
        assertNotNull(restoredReport)
        assertEquals(1, restoredReport?.userRank)
        assertEquals("100", restoredReport?.userScore)
        assertEquals("高一(1)班", restoredReport?.className)
        assertEquals(1, restoredReport?.ranks?.size)
        assertEquals("学霸", restoredReport?.ranks?.first()?.name)
        assertTrue(restoredReport?.ranks?.first()?.isSelf == true)
    }

    @Test
    fun extractClassScoreReportFromExamWithoutRankList() {
        val examData = JSONObject(
            """
            {
              "self_id": "12345",
              "title": "(new)广州八上Unit 3听说题1",
              "score": "28",
              "status": "1"
            }
            """.trimIndent()
        )
        val report = extractClassScoreReport(examData)
        assertNotNull(report)
        assertEquals("28", report?.userScore)
        assertNull(report?.userRank)
        assertTrue(report?.ranks.isNullOrEmpty())
    }

    @Test
    fun extractClassScoreReportWithRankListAndMultipleSources() {
        val scoreInfo = JSONObject(
            """
            {
              "rank_list": [
                {
                  "index": 1,
                  "real_name": "学霸",
                  "score": "30",
                  "is_self": false
                },
                {
                  "index": 2,
                  "real_name": "自己",
                  "score": "28",
                  "is_self": true
                }
              ]
            }
            """.trimIndent()
        )
        val exam = JSONObject(
            """
            {
              "self_id": "999",
              "classes": "八年级(2)班"
            }
            """.trimIndent()
        )
        val report = extractClassScoreReport(scoreInfo, exam)
        assertNotNull(report)
        assertEquals(2, report?.userRank)
        assertEquals("28", report?.userScore)
        assertEquals("八年级(2)班", report?.className)
        assertEquals(2, report?.ranks?.size)
    }

    @Test
    fun extractScoreJsonFromTextPreservesHtmlScoreAndRankCandidates() {
        val html = """
            <html>
            <head><title>考试详情</title></head>
            <body>
            <script>
            var modelData = JSON.parse('{"model_info": {"model_type": "1", "name": "模仿朗读"}}');
            var rankData = JSON.parse('{"user_rank": {"rank": 3, "score": "28"}, "self_ranks": [{"index": 3, "real_name": "测试", "score": "28", "is_self": 1}]}');
            </script>
            </body>
            </html>
        """.trimIndent()
        val json = extractScoreJsonFromText(html)
        assertNotNull(json)
        val report = extractClassScoreReport(json)
        assertNotNull(report)
        assertEquals(3, report?.userRank)
        assertEquals("28", report?.userScore)
    }
}
