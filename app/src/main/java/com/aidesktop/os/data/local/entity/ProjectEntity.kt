package com.aidesktop.os.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val repositoryUrl: String,
    val progressPercent: Int,
    val buildStatus: String, // e.g. "Passing", "Failing", "Not built"
    val notes: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "project_tasks")
data class ProjectTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val isDone: Boolean = false
)

@Entity(tableName = "project_files")
data class ProjectFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val fileName: String,
    val uriString: String
)

/**
 * ID Vault metadata ONLY — label, site, and username so the Accounts screen
 * can show a list. The password/secret itself is never stored here, never
 * stored anywhere in this app's own database, and never held in plain text.
 * It lives only in the platform credential store (Android Credential Manager
 * / the user's chosen password provider, e.g. Google Password Manager),
 * written and read via androidx.credentials — see AccountsViewModel.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,       // e.g. "GitHub", "Gmail (Work)"
    val siteDomain: String,  // e.g. "github.com" — used as the Credential Manager lookup key
    val username: String,    // e.g. an email or handle — not a secret
    val createdAt: Long = System.currentTimeMillis()
)
