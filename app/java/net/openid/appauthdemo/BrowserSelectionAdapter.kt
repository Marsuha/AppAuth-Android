/*
 * Copyright 2016 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openid.appauthdemo

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import net.openid.appauth.browser.BrowserDescriptor

/**
 * Loads the list of browsers on the device for selection in a list or spinner.
 */
class BrowserSelectionAdapter(private val context: Context) : BaseAdapter() {
    private var browsers = mutableListOf<BrowserInfo?>(null)

    data class BrowserInfo(
        val descriptor: BrowserDescriptor,
        val label: CharSequence,
        val icon: Drawable?
    )

    override fun getCount(): Int {
        return browsers.size
    }

    override fun getItem(position: Int): BrowserInfo? {
        return browsers[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.browser_selector_layout, parent, false)
            holder = ViewHolder(view)
            view.tag = holder // Сохраняем holder в теге view
        } else {
            view = convertView
            holder = view.tag as ViewHolder // Получаем закэшированный holder
        }

        val info = getItem(position)
        holder.bind(info, context)

        return view
    }

    private class ViewHolder(view: View) {
        val labelView: TextView = view.findViewById(R.id.browser_label)
        val iconView: ImageView = view.findViewById(R.id.browser_icon)

        fun bind(info: BrowserInfo?, context: Context) {
            if (info == null) {
                labelView.setText(R.string.browser_appauth_default_label)
                iconView.setImageResource(R.drawable.appauth_96dp)
            } else {
                val label = if (info.descriptor.useCustomTab) {
                    context.getString(R.string.custom_tab_label, info.label)
                } else {
                    info.label
                }

                labelView.text = label
                iconView.setImageDrawable(info.icon)
            }
        }
    }

    fun updateBrowsers(newBrowsers: List<BrowserInfo>) {
        browsers.clear()
        browsers.add(null) // Снова добавляем опцию по умолчанию
        browsers.addAll(newBrowsers)
        notifyDataSetChanged()
    }
}

