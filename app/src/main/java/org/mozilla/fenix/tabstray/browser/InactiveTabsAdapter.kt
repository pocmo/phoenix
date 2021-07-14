package org.mozilla.fenix.tabstray.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import mozilla.components.concept.tabstray.Tab
import org.mozilla.fenix.R

class InactiveTabsAdapter() : ListAdapter<Tab, InactiveTabViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InactiveTabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tab_tray_grid_item, parent, false)

        return InactiveTabViewHolder.TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: InactiveTabViewHolder, position: Int) {
    }

    private object DiffCallback : DiffUtil.ItemCallback<Tab>() {
        override fun areItemsTheSame(oldItem: Tab, newItem: Tab): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Tab, newItem: Tab): Boolean {
            return oldItem == newItem
        }
    }
}
