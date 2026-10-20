package com.ventures814.brdcrmbs

import android.app.Application
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.PlanningRepository

class BrdcrmbsApplication : Application() {
    val planningRepository: PlanningRepository by lazy {
        RoomPlanningRepository(PlanningDatabase.open(this))
    }
}
