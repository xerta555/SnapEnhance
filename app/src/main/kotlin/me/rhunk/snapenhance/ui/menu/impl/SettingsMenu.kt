package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import me.rhunk.snapenhance.ui.menu.ViewAppearanceHelper

class SettingsMenu : AbstractMenu() {
    @SuppressLint("ClickableViewAccessibility")
    private fun createCategoryTitle(key: String): TextView {
        val categoryText = TextView(context.androidContext)
        categoryText.text = context.translation.get(key)
        ViewAppearanceHelper.applyTheme(categoryText)
        categoryText.textSize = 20f
        categoryText.typeface = categoryText.typeface?.let { Typeface.create(it, Typeface.BOLD) }
        categoryText.setOnTouchListener { _, _ -> true }
        return categoryText
    }

    @SuppressLint("SetTextI18n")
    private fun createPropertyView(property: ConfigProperty): View {
        val propertyName = context.translation.get(property.nameKey)
        val updateButtonText: (TextView, String) -> Unit = { textView, text ->
            textView.text = "$propertyName${if (text.isEmpty()) "" else ": $text"}"
        }

        val updateLocalizedText: (TextView, String) -> Unit = { textView, value ->
            updateButtonText(textView, value.let {
                if (it.isEmpty()) {
                    "(empty)"
                }
                else {
                    if (property.disableValueLocalization) {
                        it
                    } else {
                        context.translation.get("option." + property.nameKey + "." + it)
                    }
                }
            })
        }

        val textEditor: ((String) -> Unit) -> Unit = { updateValue ->
            val builder = AlertDialog.Builder(context.mainActivity!!)
            builder.setTitle(propertyName)

            val input = EditText(context.androidContext)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(property.valueContainer.value().toString())

            builder.setView(input)
            builder.setPositiveButton("OK") { _, _ ->
                updateValue(input.text.toString())
            }

            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            builder.show()
        }

        val resultView: View = when (property.valueContainer) {
            is ConfigStringValue -> {
                val textView = TextView(context.androidContext)
                updateButtonText(textView, property.valueContainer.let {
                    if (it.isHidden) it.hiddenValue()
                    else it.value()
                })
                ViewAppearanceHelper.applyTheme(textView)
                textView.setOnClickListener {
                    textEditor { value ->
                        property.valueContainer.writeFrom(value)
                        updateButtonText(textView, property.valueContainer.let {
                            if (it.isHidden) it.hiddenValue()
                            else it.value()
                        })
                    }
                }
                textView
            }
            is ConfigIntegerValue -> {
                val button = Button(context.androidContext)
                updateButtonText(button, property.valueContainer.value().toString())
                button.setOnClickListener {
                    textEditor { value ->
                        runCatching {
                            property.valueContainer.writeFrom(value)
                            updateButtonText(button, value)
                        }.onFailure {
                            context.shortToast("Invalid value")
                        }
                    }
                }
                ViewAppearanceHelper.applyTheme(button)
                button
            }
            is ConfigStateValue -> {
                val switch = Switch(context.androidContext)
                switch.text = propertyName
                switch.isChecked = property.valueContainer.value()
                switch.setOnCheckedChangeListener { _, isChecked ->
                    property.valueContainer.writeFrom(isChecked.toString())
                }
                ViewAppearanceHelper.applyTheme(switch)
                switch
            }
            is ConfigStateSelection -> {
                val button = Button(context.androidContext)
                updateLocalizedText(button, property.valueContainer.value())

                button.setOnClickListener {_ ->
                    val builder = AlertDialog.Builder(context.mainActivity!!)
                    builder.setTitle(propertyName)

                    builder.setSingleChoiceItems(
                        property.valueContainer.keys().toTypedArray().map {
                            if (property.disableValueLocalization) it
                            else context.translation.get("option." + property.nameKey + "." + it)
                        }.toTypedArray(),
                        property.valueContainer.keys().indexOf(property.valueContainer.value())
                    ) { _, which ->
                        property.valueContainer.writeFrom(property.valueContainer.keys()[which])
                    }

                    builder.setPositiveButton("OK") { _, _ ->
                        updateLocalizedText(button, property.valueContainer.value())
                    }

                    builder.show()
                }
                ViewAppearanceHelper.applyTheme(button)
                button
            }
            is ConfigStateListValue -> {
                val button = Button(context.androidContext)
                updateButtonText(button, "(${property.valueContainer.value().count { it.value }})")

                button.setOnClickListener {_ ->
                    val builder = AlertDialog.Builder(context.mainActivity!!)
                    builder.setTitle(propertyName)

                    val sortedStates = property.valueContainer.value().toSortedMap()

                    builder.setMultiChoiceItems(
                        sortedStates.toSortedMap().map {
                            if (property.disableValueLocalization) it.key
                            else context.translation.get("option." + property.nameKey + "." + it.key)
                        }.toTypedArray(),
                        sortedStates.map { it.value }.toBooleanArray()
                    ) { _, which, isChecked ->
                        sortedStates.keys.toList()[which].let { key ->
                            property.valueContainer.setKey(key, isChecked)
                        }
                    }

                    builder.setPositiveButton("OK") { _, _ ->
                        updateButtonText(button, "(${property.valueContainer.value().count { it.value }})")
                    }

                    builder.show()
                }
                ViewAppearanceHelper.applyTheme(button)
                button
            }
            else -> {
                TextView(context.androidContext)
            }
        }
        return resultView
    }

    private fun newSeparator(thickness: Int, color: Int = Color.BLACK): View {
        return LinearLayout(context.mainActivity).apply {
            setPadding(0, 0, 0, thickness)
            setBackgroundColor(color)
        }
    }

    @SuppressLint("SetTextI18n")
    @Suppress("deprecation")
    fun inject(viewModel: View, addView: (View) -> Unit) {
        val packageInfo = viewModel.context.packageManager.getPackageInfo(Constants.SNAPCHAT_PACKAGE_NAME, 0)
        val versionTextBuilder = StringBuilder()
        versionTextBuilder.append("SnapEnhance ").append(BuildConfig.VERSION_NAME)
            .append(" by rhunk")
        if (BuildConfig.DEBUG) {
            versionTextBuilder.append("\n").append("Snapchat ").append(packageInfo.versionName)
                .append(" (").append(packageInfo.longVersionCode).append(")")
        }
        val titleText = TextView(viewModel.context)
        titleText.text = versionTextBuilder.toString()
        ViewAppearanceHelper.applyTheme(titleText)
        titleText.textSize = 18f
        titleText.minHeight = 80 * versionTextBuilder.chars().filter { ch: Int -> ch == '\n'.code }
                .count().coerceAtLeast(2).toInt()
        addView(titleText)

        val actions = context.actionManager.getActions().map {
            Pair(it) {
                val button = Button(viewModel.context)
                button.text = context.translation.get(it.nameKey)
                button.setOnClickListener { _ ->
                    it.run()
                }
                ViewAppearanceHelper.applyTheme(button)
                button
            }
        }

        context.config.entries().groupBy {
            it.key.category
        }.forEach { (category, value) ->
            addView(createCategoryTitle(category.key))
            value.filter { it.key.shouldAppearInSettings }.forEach { (property, _) ->
                addView(createPropertyView(property))
                actions.find { pair -> pair.first.dependsOnProperty == property}?.let { pair ->
                    addView(pair.second())
                }
            }
        }

        actions.filter { it.first.dependsOnProperty == null }.forEach {
            addView(it.second())
        }

        addView(newSeparator(3, Color.parseColor("#f5f5f5")))
    }
}