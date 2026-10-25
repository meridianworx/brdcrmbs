package com.ventures814.brdcrmbs.backup

import com.ventures814.brdcrmbs.debugqa.ActiveFastQaOwnership
import com.ventures814.brdcrmbs.debugqa.QaOwnership

internal fun isBackupQaOwned(id: String): Boolean = id == ActiveFastQaOwnership.ID || QaOwnership.weekOf(id) != null
