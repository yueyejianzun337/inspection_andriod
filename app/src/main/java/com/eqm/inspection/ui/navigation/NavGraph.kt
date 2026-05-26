package com.eqm.inspection.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val INSPECTION = "inspection"
    const val QUERY = "query"
    const val DETAIL = "detail/{recordId}"
    const val DRAFTS = "drafts"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val REVIEW_INSPECTION = "inspection_review/{recordId}"

    fun inspectionEdit(draftId: Int) = "inspection?draftId=$draftId"
    fun detail(recordId: Int) = "detail/$recordId"
    fun reviewEdit(recordId: Int) = "inspection_review/$recordId"
}
