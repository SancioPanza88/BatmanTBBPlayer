package com.toonitalia.batmanplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.toonitalia.batmanplayer.data.SeriesRepository
import com.toonitalia.batmanplayer.databinding.ActivityMainBinding
import com.toonitalia.batmanplayer.model.Episode
import com.toonitalia.batmanplayer.model.Series
import com.toonitalia.batmanplayer.ui.EpisodeAdapter
import com.toonitalia.batmanplayer.ui.Row
import com.toonitalia.batmanplayer.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var repository: SeriesRepository
    private lateinit var adapter: EpisodeAdapter

    private var series: Series? = null
    private var query: String = ""
    private val collapsedSeasons = mutableSetOf<Int>()

    private val openJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        repository = SeriesRepository(applicationContext)

        adapter = EpisodeAdapter(
            onEpisodeClick = { episode, _ -> openEpisode(episode, sourceIndex = prefs.sourceIndex) },
            onEpisodeLongClick = { episode, _ -> showSourceChooser(episode) },
            onSeasonToggle = { season ->
                if (!collapsedSeasons.remove(season.number)) collapsedSeasons.add(season.number)
                refreshRows()
            }
        )

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabOpen.setOnClickListener { openJsonPicker() }
        setupToolbar()
        loadBundledSeries()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item -> handleMenuItem(item) }

        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                setQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                setQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_toggle_adblock -> {
                prefs.adBlockEnabled = !prefs.adBlockEnabled
                Snackbar.make(
                    binding.root,
                    if (prefs.adBlockEnabled) R.string.toggle_adblock else R.string.adblock_off,
                    Snackbar.LENGTH_SHORT
                ).show()
                return true
            }

            R.id.action_toggle_native -> {
                prefs.preferNativePlayer = !prefs.preferNativePlayer
                Snackbar.make(
                    binding.root,
                    if (prefs.preferNativePlayer) R.string.native_player else R.string.web_player,
                    Snackbar.LENGTH_SHORT
                ).show()
                return true
            }

            R.id.action_source -> {
                showDefaultSourceDialog()
                return true
            }

            R.id.action_open_json -> {
                openJsonPicker()
                return true
            }
        }
        return false
    }

    private fun showDefaultSourceDialog() {
        val labels = arrayOf(getString(R.string.source_uqload), getString(R.string.source_chuckle))
        val checked = if (prefs.sourceIndex == 1) 1 else 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_source)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.sourceIndex = which
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openJsonPicker() {
        try {
            openJsonLauncher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
            )
        } catch (e: Exception) {
            Snackbar.make(binding.root, R.string.json_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun loadBundledSeries() {
        binding.progress.isVisible = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.loadBundled() } }
            binding.progress.isVisible = false
            result
                .onSuccess { applySeries(it) }
                .onFailure {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.json_error, it.message ?: ""),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun loadFromUri(uri: Uri) {
        binding.progress.isVisible = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.loadFromUri(uri) } }
            binding.progress.isVisible = false
            result
                .onSuccess {
                    applySeries(it)
                    Snackbar.make(
                        binding.root,
                        getString(R.string.json_loaded, it.title),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.json_error, it.message ?: ""),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun applySeries(loaded: Series) {
        series = loaded
        collapsedSeasons.clear()
        query = ""
        refreshRows()
    }

    private fun setQuery(text: String) {
        query = text
        refreshRows()
    }

    private fun refreshRows() {
        val current = series ?: return
        val rows = mutableListOf<Row>()
        rows += Row.Header(current)

        val needle = query.trim().lowercase()
        var total = 0
        current.seasons.forEach { season ->
            val episodes = if (needle.isBlank()) {
                season.episodes
            } else {
                season.episodes.filter { it.title.lowercase().contains(needle) }
            }
            if (episodes.isEmpty()) return@forEach
            total += episodes.size
            val expanded = needle.isNotBlank() || season.number !in collapsedSeasons
            rows += Row.SeasonHeader(season.copy(episodes = episodes), expanded)
            if (expanded) {
                episodes.forEach { rows += Row.EpisodeItem(it, current) }
            }
        }
        if (total == 0 && needle.isNotBlank()) {
            Snackbar.make(binding.root, R.string.no_episodes, Snackbar.LENGTH_SHORT).show()
        }
        adapter.submitList(rows)
    }

    private fun showSourceChooser(episode: Episode) {
        val labels = arrayOf(getString(R.string.source_uqload), getString(R.string.source_chuckle))
        MaterialAlertDialogBuilder(this)
            .setTitle(episode.title)
            .setItems(labels) { _, which -> openEpisode(episode, sourceIndex = which) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openEpisode(episode: Episode, sourceIndex: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_TITLE, episode.title)
            putExtra(PlayerActivity.EXTRA_SEASON, episode.season)
            putExtra(PlayerActivity.EXTRA_NUMBER, episode.number)
            putExtra(PlayerActivity.EXTRA_URL1, episode.player1)
            putExtra(PlayerActivity.EXTRA_URL2, episode.player2)
            putExtra(PlayerActivity.EXTRA_SOURCE, sourceIndex)
        }
        startActivity(intent)
    }
}
