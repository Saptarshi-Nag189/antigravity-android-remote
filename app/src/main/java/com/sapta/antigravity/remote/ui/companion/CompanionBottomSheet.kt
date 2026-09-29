package com.sapta.antigravity.remote.ui.companion

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.sapta.antigravity.remote.R
import com.sapta.antigravity.remote.data.AppPreferences
import java.io.File

/**
 * CompanionBottomSheet
 *
 * Encapsulates the Google Material 3 Companion Bottom Sheet HUD.
 * Manages identity switching, PC onboarding guidance, quick slash commands,
 * and session settings.
 */
class CompanionBottomSheet(
    private val context: Context,
    private val preferences: AppPreferences,
    private val callbacks: CompanionCallbacks
) {

    interface CompanionCallbacks {
        fun onNavigate(url: String)
        fun onInsertCommand(command: String)
        fun onSignOutRequested()
        fun onKeepAliveToggled(enabled: Boolean)
        fun onDeskModeToggled(enabled: Boolean)
        fun onReloadRequested()
    }

    private var dialog: BottomSheetDialog? = null
    private var currentSheetView: View? = null

    fun show() {
        val bottomSheet = BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_companion, null)
        bottomSheet.setContentView(view)
        currentSheetView = view

        setupAccountCard(view, bottomSheet)
        setupOnboardingSection(view)
        setupRemoteLinkCard(view, bottomSheet)
        setupDeviceStatusCard(view, bottomSheet)
        setupQuickCommands(view, bottomSheet)
        setupSessionOptions(view)
        setupFooter(view, bottomSheet)

        // Close button
        view.findViewById<ImageView>(R.id.btnDismissSheet)?.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.setOnDismissListener {
            currentSheetView = null
            dialog = null
        }

        bottomSheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        bottomSheet.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { frame ->
            val behavior = BottomSheetBehavior.from(frame)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        bottomSheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.behavior.skipCollapsed = true
        dialog = bottomSheet
        bottomSheet.show()
    }

    fun refreshAccountView() {
        val view = currentSheetView ?: return
        val sheet = dialog ?: return
        if (!sheet.isShowing) return
        setupAccountCard(view, sheet)
        setupOnboardingSection(view)
    }

    private fun setupAccountCard(view: View, sheet: Dialog) {
        val rowPrimary = view.findViewById<LinearLayout>(R.id.rowAccountPrimary)
        val txtName = view.findViewById<TextView>(R.id.txtPrimaryName)
        val txtEmail = view.findViewById<TextView>(R.id.txtPrimaryEmail)
        val badgePro = view.findViewById<TextView>(R.id.badgePro)
        val imgAvatar = view.findViewById<ShapeableImageView>(R.id.imgPrimaryAvatar)
        val btnChevron = view.findViewById<FrameLayout>(R.id.btnChevronExpand)
        val layoutOther = view.findViewById<LinearLayout>(R.id.layoutOtherAccounts)
        val btnManage = view.findViewById<MaterialButton>(R.id.btnManageGoogleAccount)
        val rowAdd = view.findViewById<LinearLayout>(R.id.rowAddAccount)
        val rowSignOut = view.findViewById<LinearLayout>(R.id.rowSignOut)

        // Populate name & email from runtime active session
        val currentName = preferences.cachedUserName
        val currentEmail = preferences.cachedUserEmail

        txtName.text = currentName
        txtEmail.text = currentEmail

        // Tap on primary account row to edit / customize profile details
        rowPrimary.setOnClickListener {
            showEditProfileDialog()
        }

        // Pro badge
        badgePro.visibility = if (preferences.isUserPro) View.VISIBLE else View.GONE

        // Load avatar if cached locally
        val cachedAvatar = File(context.filesDir, "cached_avatar_primary.png")
        if (cachedAvatar.exists() && cachedAvatar.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(cachedAvatar.absolutePath)
                if (bitmap != null) {
                    imgAvatar.setImageBitmap(bitmap)
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
                }
            } catch (_: Exception) {
                imgAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }
        } else {
            imgAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
        }

        // Chevron expand/collapse
        var isExpanded = false
        layoutOther.visibility = View.GONE
        btnChevron.setOnClickListener {
            isExpanded = !isExpanded
            layoutOther.visibility = if (isExpanded) View.VISIBLE else View.GONE
            btnChevron.animate().rotation(if (isExpanded) 180f else 0f).setDuration(180).start()
        }

        btnManage.setOnClickListener {
            sheet.dismiss()
            callbacks.onNavigate("https://myaccount.google.com/")
        }

        rowAdd.setOnClickListener {
            sheet.dismiss()
            callbacks.onNavigate("https://accounts.google.com/AddSession?continue=https%3A%2F%2Fantigravity.google.com%2F")
        }

        rowSignOut.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_sign_out_title)
                .setMessage(R.string.dialog_sign_out_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_sign_out) { _, _ ->
                    sheet.dismiss()
                    callbacks.onSignOutRequested()
                }
                .show()
        }
    }

    private fun showEditProfileDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val editName = EditText(context).apply {
            hint = "Display name"
            setText(preferences.cachedUserName)
            setTextColor(context.getColor(R.color.google_text_primary))
            setHintTextColor(context.getColor(R.color.google_text_muted))
            textSize = 14f
            setSingleLine(true)
        }

        val editEmail = EditText(context).apply {
            hint = "Email address"
            setText(preferences.cachedUserEmail)
            setTextColor(context.getColor(R.color.google_text_primary))
            setHintTextColor(context.getColor(R.color.google_text_muted))
            textSize = 14f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }

        layout.addView(editName)
        layout.addView(editEmail)

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_edit_profile_title)
            .setView(layout)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val newName = editName.text.toString().trim()
                val newEmail = editEmail.text.toString().trim()
                if (newName.isNotBlank()) preferences.cachedUserName = newName
                if (newEmail.isNotBlank()) preferences.cachedUserEmail = newEmail
                refreshAccountView()
                Toast.makeText(context, R.string.toast_account_updated, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setupOnboardingSection(view: View) {
        val cardOnboarding = view.findViewById<LinearLayout>(R.id.cardOnboardingInstructions)
        // Show onboarding instructions when no remote session link has been established
        if (preferences.lastRemoteLink.isNotBlank()) {
            cardOnboarding.visibility = View.GONE
        } else {
            cardOnboarding.visibility = View.VISIBLE
        }
    }

    private fun setupRemoteLinkCard(view: View, sheet: Dialog) {
        val editLink = view.findViewById<EditText>(R.id.editRemoteLink)
        val btnPaste = view.findViewById<MaterialButton>(R.id.btnPasteLink)
        val btnConnect = view.findViewById<MaterialButton>(R.id.btnConnectLink)

        if (preferences.lastRemoteLink.isNotBlank()) {
            editLink.setText(preferences.lastRemoteLink)
        }

        // Smooth scroll to card when user focuses on the link input
        editLink.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                view.postDelayed({
                    val scrollView = view.findViewById<NestedScrollView>(R.id.sheetScrollView)
                    val card = view.findViewById<View>(R.id.cardRemoteLink)
                    if (scrollView != null && card != null) {
                        scrollView.smoothScrollTo(0, card.top)
                    }
                }, 150)
            }
        }

        btnPaste.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).coerceToText(context).toString().trim()
                if (pasteText.startsWith("http://") || pasteText.startsWith("https://")) {
                    editLink.setText(pasteText)
                    Toast.makeText(context, R.string.action_paste, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnConnect.setOnClickListener {
            val link = editLink.text.toString().trim()
            if (link.isNotBlank() && (link.startsWith("http://") || link.startsWith("https://"))) {
                preferences.lastRemoteLink = link
                sheet.dismiss()
                callbacks.onNavigate(link)
                Toast.makeText(context, R.string.toast_connected, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.toast_link_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDeviceStatusCard(view: View, sheet: Dialog) {
        val txtHost = view.findViewById<TextView>(R.id.txtDeviceHost)
        val txtSpecs = view.findViewById<TextView>(R.id.txtDeviceSpecs)
        val btnHealth = view.findViewById<MaterialButton>(R.id.btnCheckPrimaryStats)

        txtHost.text = preferences.workstationHostname
        btnHealth.setOnClickListener {
            val host = preferences.workstationHostname
            val prompt = "System health check: Please inspect the current CPU usage, RAM utilization, SSD disk space, thermal profile, and running processes on workstation '$host'."
            sheet.dismiss()
            callbacks.onInsertCommand(prompt)
        }
    }

    private fun setupQuickCommands(view: View, sheet: Dialog) {
        val commands = mapOf(
            R.id.btnCmdGoal to "/goal",
            R.id.btnCmdPlan to "/plan",
            R.id.btnCmdBrowser to "/browser",
            R.id.btnCmdBoost to "/boost",
            R.id.btnCmdSchedule to "/schedule",
            R.id.btnCmdLearn to "/learn"
        )

        for ((id, cmd) in commands) {
            view.findViewById<MaterialButton>(id)?.setOnClickListener {
                sheet.dismiss()
                callbacks.onInsertCommand(cmd)
            }
        }
    }

    private fun setupSessionOptions(view: View) {
        val switchKeepAlive = view.findViewById<MaterialSwitch>(R.id.switchBackgroundKeepAlive)
        val switchDeskMode = view.findViewById<MaterialSwitch>(R.id.switchDeskMode)
        val btnReload = view.findViewById<MaterialButton>(R.id.btnReloadChat)
        val badgeStatus = view.findViewById<TextView>(R.id.badgeBgStatus)

        switchKeepAlive.isChecked = preferences.isKeepAliveEnabled
        badgeStatus.text = if (preferences.isKeepAliveEnabled) {
            context.getString(R.string.status_active)
        } else {
            context.getString(R.string.status_disconnected)
        }

        switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            preferences.isKeepAliveEnabled = isChecked
            badgeStatus.text = if (isChecked) {
                context.getString(R.string.status_active)
            } else {
                context.getString(R.string.status_disconnected)
            }
            callbacks.onKeepAliveToggled(isChecked)
        }

        switchDeskMode.isChecked = preferences.isDeskModeEnabled
        switchDeskMode.setOnCheckedChangeListener { _, isChecked ->
            preferences.isDeskModeEnabled = isChecked
            callbacks.onDeskModeToggled(isChecked)
        }

        btnReload.setOnClickListener {
            dialog?.dismiss()
            callbacks.onReloadRequested()
        }
    }

    private fun setupFooter(view: View, sheet: Dialog) {
        view.findViewById<TextView>(R.id.txtPrivacyTerms)?.setOnClickListener {
            sheet.dismiss()
            callbacks.onNavigate("https://policies.google.com/privacy")
        }
    }
}
