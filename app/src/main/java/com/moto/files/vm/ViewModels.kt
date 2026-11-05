package com.moto.files.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moto.files.MotoFilesApp
import com.moto.files.data.SettingsStore
import com.moto.files.model.*
import com.moto.files.repo.BinRepository
import com.moto.files.repo.FileRepository
import com.moto.files.repo.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FilesVm(app: MotoFilesApp) : ViewModel() {
    private val repo: FileRepository = app.files
    private val bin: BinRepository = app.bin
    private val settings: SettingsStore = app.settings

    private val _dir = MutableStateFlow(repo.internal)
    val dir: StateFlow<File> = _dir.asStateFlow()
    private val _entries = MutableStateFlow<List<FsEntry>>(emptyList())
    val entries: StateFlow<List<FsEntry>> = _entries.asStateFlow()
    private val _picked = MutableStateFlow<Set<String>>(emptySet())
    val picked: StateFlow<Set<String>> = _picked.asStateFlow()
    private val _storage = MutableStateFlow<Pair<Long, Long>?>(null)
    val storage: StateFlow<Pair<Long, Long>?> = _storage.asStateFlow()

    enum class Clip { CUT, COPY }
    private val _clip = MutableStateFlow<Pair<Clip, List<File>>?>(null)
    val clip: StateFlow<Pair<Clip, List<File>>?> = _clip.asStateFlow()

    fun open(d: File = _dir.value) {
        _dir.value = d
        viewModelScope.launch {
            _entries.value = repo.list(d, settings.load().showHidden)
            _storage.value = repo.storage(d)
        }
    }

    fun openCategory(c: Category) = viewModelScope.launch {
        _entries.value = repo.byCategory(c)
    }

    fun toggle(path: String) {
        _picked.value = _picked.value.toMutableSet().apply { if (!add(path)) remove(path) }
    }

    fun clearPicked() { _picked.value = emptySet() }

    fun pickedFiles(): List<File> {
        val sel = _picked.value
        return _entries.value.filter { it.path in sel }.map { File(it.path) }
    }

    fun cut() { pickedFiles().takeIf { it.isNotEmpty() }?.let { _clip.value = Clip.CUT to it }; clearPicked() }
    fun copy() { pickedFiles().takeIf { it.isNotEmpty() }?.let { _clip.value = Clip.COPY to it }; clearPicked() }

    fun paste() = viewModelScope.launch {
        val (mode, files) = _clip.value ?: return@launch
        if (mode == Clip.CUT) repo.move(files, _dir.value) else repo.copy(files, _dir.value)
        if (mode == Clip.CUT) _clip.value = null
        open()
    }

    fun trashSelected() = viewModelScope.launch {
        pickedFiles().forEach { bin.delete(it) }
        clearPicked(); open()
    }

    fun deleteForeverSelected() = viewModelScope.launch {
        pickedFiles().forEach { bin.deleteForever(it) }
        clearPicked(); open()
    }

    fun rename(path: String, name: String) = viewModelScope.launch {
        repo.rename(File(path), name); open()
    }

    fun up() { _dir.value.parentFile?.let { open(it) } }
}

class GalleryVm(app: MotoFilesApp) : ViewModel() {
    private val repo: MediaRepository = app.media

    private val _items = MutableStateFlow<List<Media>>(emptyList())
    val items: StateFlow<List<Media>> = _items.asStateFlow()
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()
    private val _memories = MutableStateFlow<List<Album>>(emptyList())
    val memories: StateFlow<List<Album>> = _memories.asStateFlow()
    private val _cols = MutableStateFlow(app.settings.load().gridColumns)
    val cols: StateFlow<Int> = _cols.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            app.settings.observe().collect { _cols.value = it.gridColumns }
        }
    }

    fun load(force: Boolean = false) = viewModelScope.launch {
        if (loaded && !force) return@launch
        loaded = true
        _items.value = repo.all()
        _albums.value = repo.albums()
        _memories.value = repo.memories()
    }
}

class BinVm(app: MotoFilesApp) : ViewModel() {
    private val repo: BinRepository = app.bin

    private val _items = MutableStateFlow<List<BinItem>>(emptyList())
    val items: StateFlow<List<BinItem>> = _items.asStateFlow()

    fun refresh() = viewModelScope.launch { _items.value = repo.list() }
    fun restore(item: BinItem) = viewModelScope.launch { repo.restore(item); refresh() }
    fun empty() = viewModelScope.launch { repo.empty(); refresh() }
}

class SettingsVm(app: MotoFilesApp) : ViewModel() {
    private val store = app.settings
    val settings: StateFlow<Settings> = store.observe().let { flow ->
        MutableStateFlow(flow.value).also { state ->
            viewModelScope.launch { flow.collect { state.value = it } }
        }.asStateFlow()
    }

    fun update(block: (Settings) -> Settings) {
        store.save(block(store.load()))
    }
}
