package org.mozilla.fenix.tabstray.browser

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import mozilla.components.concept.tabstray.Tab

sealed class InactiveTabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    class TabViewHolder(itemView: View) : InactiveTabViewHolder(itemView) {
        fun bind(tab: Tab) {
            tab.hashCode()
        }
    }
}
