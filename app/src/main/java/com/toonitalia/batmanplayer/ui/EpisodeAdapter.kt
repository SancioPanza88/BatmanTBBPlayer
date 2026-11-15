package com.toonitalia.batmanplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.toonitalia.batmanplayer.R
import com.toonitalia.batmanplayer.databinding.ItemEpisodeBinding
import com.toonitalia.batmanplayer.databinding.ItemHeaderBinding
import com.toonitalia.batmanplayer.databinding.ItemSeasonBinding
import com.toonitalia.batmanplayer.model.Episode
import com.toonitalia.batmanplayer.model.Season
import com.toonitalia.batmanplayer.model.Series

/** Righe della lista principale: intestazione, intestazione stagione, episodio. */
sealed class Row {
    abstract val key: String

    data class Header(val series: Series) : Row() {
        override val key: String get() = "header"
    }

    data class SeasonHeader(val season: Season, val expanded: Boolean) : Row() {
        override val key: String get() = "season_${season.number}"
    }

    data class EpisodeItem(val episode: Episode, val series: Series) : Row() {
        override val key: String get() = "ep_${episode.season}_${episode.number}"
    }
}

class EpisodeAdapter(
    private val onEpisodeClick: (Episode, Series) -> Unit,
    private val onEpisodeLongClick: (Episode, Series) -> Unit,
    private val onSeasonToggle: (Season) -> Unit
) : ListAdapter<Row, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SEASON = 1
        private const val TYPE_EPISODE = 2

        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Row.Header -> TYPE_HEADER
        is Row.SeasonHeader -> TYPE_SEASON
        is Row.EpisodeItem -> TYPE_EPISODE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false))
            TYPE_SEASON -> SeasonHolder(ItemSeasonBinding.inflate(inflater, parent, false))
            else -> EpisodeHolder(ItemEpisodeBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is Row.Header -> (holder as HeaderHolder).bind(row.series)
            is Row.SeasonHeader -> (holder as SeasonHolder).bind(row.season, row.expanded)
            is Row.EpisodeItem -> (holder as EpisodeHolder).bind(row.episode, row.series)
        }
    }

    private inner class HeaderHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(series: Series) {
            val ctx = binding.root.context
            binding.headerTitle.text = series.title
            binding.headerSubtitle.text = ctx.getString(
                R.string.metadata,
                series.country.ifBlank { "-" },
                series.releaseDate.ifBlank { "-" },
                series.status.ifBlank { "-" }
            )
            binding.headerChips.removeAllViews()
            addChip(ctx.getString(R.string.episodes_count, series.episodeCount))
            if (series.streamingUpdate.isNotBlank()) addChip(series.streamingUpdate)
            if (series.originalTitle.isNotBlank() && series.originalTitle != series.title) {
                addChip(series.originalTitle)
            }
            binding.headerSource.text = if (series.sourceUrl.isNotBlank()) {
                ctx.getString(R.string.source_link, series.sourceUrl)
            } else {
                ""
            }
        }

        private fun addChip(label: String) {
            val chip = Chip(binding.root.context).apply {
                text = label
                isClickable = false
                isCheckable = false
            }
            binding.headerChips.addView(chip)
        }
    }

    private inner class SeasonHolder(private val binding: ItemSeasonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(season: Season, expanded: Boolean) {
            val ctx = binding.root.context
            binding.seasonTitle.text = ctx.getString(R.string.season_title, season.number)
            binding.seasonCount.text = ctx.getString(R.string.season_count, season.episodes.size)
            binding.seasonArrow.setImageResource(
                if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            binding.root.setOnClickListener { onSeasonToggle(season) }
        }
    }

    private inner class EpisodeHolder(private val binding: ItemEpisodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: Episode, series: Series) {
            val ctx = binding.root.context
            binding.episodeNumber.text = episode.number.toString()
            binding.episodeTitle.text = episode.title
            val sources = buildList {
                if (episode.player1.isNotBlank()) add(ctx.getString(R.string.source_uqload))
                if (episode.player2.isNotBlank()) add(ctx.getString(R.string.source_chuckle))
            }
            binding.episodeSources.text = sources.joinToString("  •  ")
            binding.root.setOnClickListener { onEpisodeClick(episode, series) }
            binding.root.setOnLongClickListener {
                onEpisodeLongClick(episode, series)
                true
            }
        }
    }
}
