package com.ventures814.brdcrmbs

import android.app.Application
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.PlanningRepository

class BrdcrmbsApplication : Application() {
    internal val planningDatabase: PlanningDatabase by lazy { PlanningDatabase.open(this) }
    val planningRepository: PlanningRepository by lazy {
        RoomPlanningRepository(planningDatabase)
    }
}
