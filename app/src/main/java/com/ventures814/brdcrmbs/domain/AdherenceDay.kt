package com.ventures814.brdcrmbs.domain

import java.time.LocalDate
import java.time.ZoneId

/** The local date/zone being followed; repositories reject a stale Today context. */
data class AdherenceDay(val date: LocalDate, val zone: ZoneId)
