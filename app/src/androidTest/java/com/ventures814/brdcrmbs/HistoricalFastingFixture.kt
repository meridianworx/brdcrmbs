package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.data.local.*
import com.ventures814.brdcrmbs.domain.*

/** Test-only simulation of rows written before integrity validation existed. Never a production API. */
internal suspend fun PlanningDatabase.insertHistoricalFasting(plan: FastingPlan) {
    when (plan) {
        is FastingEvent -> planningDao().insert(plan.entity())
        is NoFastPlan -> planningDao().insert(plan.entity())
    }
}
