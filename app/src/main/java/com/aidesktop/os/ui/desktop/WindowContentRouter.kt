package com.aidesktop.os.ui.desktop

import androidx.compose.runtime.Composable
import com.aidesktop.os.domain.model.AppKind
import com.aidesktop.os.ui.aichat.AiChatWindowContent
import com.aidesktop.os.ui.browser.BrowserWindowContent
import com.aidesktop.os.ui.coderunner.CodeRunnerWindowContent
import com.aidesktop.os.ui.projects.ProjectsWindowContent
import com.aidesktop.os.ui.settings.AccountsWindowContent
import com.aidesktop.os.ui.settings.FileManagerWindowContent
import com.aidesktop.os.ui.settings.SettingsWindowContent

/** Dispatches a window's body to the correct mini-app root composable. */
@Composable
fun WindowContentRouter(kind: AppKind) {
    when (kind) {
        AppKind.BROWSER -> BrowserWindowContent()
        AppKind.AI_CHAT -> AiChatWindowContent()
        AppKind.PROJECTS -> ProjectsWindowContent()
        AppKind.FILE_MANAGER -> FileManagerWindowContent()
        AppKind.CODE_RUNNER -> CodeRunnerWindowContent()
        AppKind.SETTINGS -> SettingsWindowContent()
        AppKind.ACCOUNTS -> AccountsWindowContent()
    }
}
