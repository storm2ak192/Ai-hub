#!/usr/bin/env python3
from pathlib import Path

# HubViewModel: default Favorites + reorderSites
vm = Path("app/src/main/java/com/example/ui/viewmodel/HubViewModel.kt")
t = vm.read_text(encoding="utf-8")
t = t.replace(
    "private val _selectedTab = MutableStateFlow(BottomNavTab.HOME)",
    "private val _selectedTab = MutableStateFlow(BottomNavTab.FAVORITES)",
)
old = """    fun moveSiteOrder(siteId: String, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repository.moveSiteOrder(siteId, moveUp) }
    }"""
new = """    fun moveSiteOrder(siteId: String, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repository.moveSiteOrder(siteId, moveUp) }
    }

    fun reorderSites(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) { repository.reorderSites(fromIndex, toIndex) }
    }"""
if "fun reorderSites" not in t and old in t:
    t = t.replace(old, new, 1)
    print("OK: ViewModel reorderSites")
elif "fun reorderSites" in t:
    print("OK: ViewModel reorder already")
else:
    print("WARN: moveSiteOrder not found")
vm.write_text(t, encoding="utf-8")
print("OK: default tab", "FAVORITES" in t)

# HubRepository: reorderSites
repo = Path("app/src/main/java/com/example/data/repository/HubRepository.kt")
t = repo.read_text(encoding="utf-8")
if "suspend fun reorderSites" not in t:
    marker = """        val reindexed = ordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        siteDao.updateSites(reindexed)
    }

    suspend fun restoreDefaultSites()"""
    insert = """        val reindexed = ordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        siteDao.updateSites(reindexed)
    }

    suspend fun reorderSites(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val ordered = siteDao.getAllSitesSnapshot().sortedBy { it.sortOrder }.toMutableList()
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices) return
        val item = ordered.removeAt(fromIndex)
        ordered.add(toIndex, item)
        siteDao.updateSites(ordered.mapIndexed { idx, s -> s.copy(sortOrder = idx) })
    }

    suspend fun restoreDefaultSites()"""
    if marker in t:
        t = t.replace(marker, insert, 1)
        repo.write_text(t, encoding="utf-8")
        print("OK: Repository reorderSites")
    else:
        print("WARN: repository marker missing")
else:
    print("OK: Repository reorder already")
