package me.rhunk.snapenhance.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.download.MediaDownloadReceiver
import me.rhunk.snapenhance.download.data.PendingDownload

class DownloadManagerActivity : Activity() {
    private val fetchedDownloadTasks = mutableListOf<PendingDownload>()
    private var listFilter = MediaFilter.NONE

    private val downloadTaskManager by lazy {
        MediaDownloadReceiver.downloadTaskManager.also { it.init(this) }
    }

    private val preferences by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private fun updateNoDownloadText() {
        findViewById<View>(R.id.no_download_title).let {
            it.visibility = if (fetchedDownloadTasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateListContent() {
        fetchedDownloadTasks.clear()
        fetchedDownloadTasks.addAll(downloadTaskManager.queryAllTasks(filter = listFilter).values)

        with(findViewById<RecyclerView>(R.id.download_list)) {
            adapter?.notifyDataSetChanged()
            scrollToPosition(0)
        }
        updateNoDownloadText()
    }

    @SuppressLint("BatteryLife", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.download_manager_activity)
        
        window.navigationBarColor = getColor(R.color.primaryBackground)

        with(findViewById<RecyclerView>(R.id.download_list)) {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@DownloadManagerActivity)

            adapter = DownloadListAdapter(fetchedDownloadTasks).apply {
                registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                        updateNoDownloadText()
                    }
                })
            }

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val download = fetchedDownloadTasks[viewHolder.absoluteAdapterPosition]
                    return if (download.isJobActive()) {
                        0
                    } else {
                        super.getMovementFlags(recyclerView, viewHolder)
                    }
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                @SuppressLint("NotifyDataSetChanged")
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    fetchedDownloadTasks.removeAt(viewHolder.absoluteAdapterPosition).let {
                        downloadTaskManager.removeTask(it)
                    }
                    adapter?.notifyItemRemoved(viewHolder.absoluteAdapterPosition)
                }
            }).attachToRecyclerView(this)

            var isLoading = false

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                    if (lastVisibleItemPosition == RecyclerView.NO_POSITION) {
                        return
                    }

                    if (lastVisibleItemPosition == fetchedDownloadTasks.size - 1 && !isLoading) {
                        isLoading = true

                        downloadTaskManager.queryTasks(fetchedDownloadTasks.last().id, filter = listFilter).forEach {
                            fetchedDownloadTasks.add(it.value)
                            adapter?.notifyItemInserted(fetchedDownloadTasks.size - 1)
                        }

                        isLoading = false
                    }
                }
            })
    
            arrayOf(
                Pair(R.id.all_category, MediaFilter.NONE),
                Pair(R.id.pending_category, MediaFilter.PENDING),
                Pair(R.id.snap_category, MediaFilter.CHAT_MEDIA),
                Pair(R.id.story_category, MediaFilter.STORY),
                Pair(R.id.spotlight_category, MediaFilter.SPOTLIGHT)
            ).let { categoryPairs ->
                categoryPairs.forEach { pair ->
                    this@DownloadManagerActivity.findViewById<TextView>(pair.first).setOnClickListener { view ->
                        listFilter = pair.second
                        updateListContent()
                        categoryPairs.map { this@DownloadManagerActivity.findViewById<TextView>(it.first) }.forEach {
                            it.setTextColor(getColor(R.color.primaryText))
                        }
                        (view as TextView).setTextColor(getColor(R.color.focusedCategoryColor))
                    }
                }
            }

            this@DownloadManagerActivity.findViewById<Button>(R.id.remove_all_button).setOnClickListener {
                with(AlertDialog.Builder(this@DownloadManagerActivity)) {
                    setTitle(R.string.remove_all_title)
                    setMessage(R.string.remove_all_text)
                    setPositiveButton("Yes") { _, _ ->
                        downloadTaskManager.removeAllTasks()
                        fetchedDownloadTasks.removeIf {
                            if (it.isJobActive()) it.cancel()
                            true
                        }
                        adapter?.notifyDataSetChanged()
                        updateNoDownloadText()
                    }
                    setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
            }

        }

        updateListContent()

        if (!preferences.getBoolean("ask_battery_optimisations", true) ||
            !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) return

        with(Intent()) {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
            startActivityForResult(this, 1)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            preferences.edit().putBoolean("ask_battery_optimisations", false).apply()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        updateListContent()
    }
}