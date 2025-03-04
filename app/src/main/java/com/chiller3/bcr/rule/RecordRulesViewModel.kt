/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.GroupLookup
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.getContactByLookupKey
import com.chiller3.bcr.getContactGroupById
import com.chiller3.bcr.withContactsByUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class DisplayedRecordRule : Comparable<DisplayedRecordRule> {
    abstract var record: Boolean

    protected abstract val sortCategory: Int

    /**
     * Rule types are sorted by [sortCategory]. Rules within the same category are sorted by the
     * display name if possible.
     */
    override fun compareTo(other: DisplayedRecordRule): Int {
        when (this) {
            is AllCalls -> if (other is AllCalls) {
                return record.compareTo(other.record)
            }
            is UnknownCalls -> if (other is UnknownCalls) {
                return record.compareTo(other.record)
            }
            is Contact -> if (other is Contact) {
                return compareValuesBy(
                    this,
                    other,
                    { it.displayName },
                    { it.lookupKey },
                    { it.record },
                )
            }
            is ContactGroup -> if (other is ContactGroup) {
                return compareValuesBy(
                    this,
                    other,
                    { it.title },
                    { it.accountName },
                    { it.rowId },
                    { it.sourceId },
                    { it.record },
                )
            }
        }

        return sortCategory.compareTo(other.sortCategory)
    }

    data class AllCalls(override var record: Boolean) : DisplayedRecordRule() {
        override val sortCategory: Int = 4
    }

    data class UnknownCalls(override var record: Boolean) : DisplayedRecordRule() {
        override val sortCategory: Int = 3
    }

    data class Contact(
        val lookupKey: String,
        override var record: Boolean,
        val displayName: String?,
    ) : DisplayedRecordRule() {
        override val sortCategory: Int = 1
    }

    data class ContactGroup(
        val rowId: Long,
        val sourceId: String?,
        override var record: Boolean,
        val title: String?,
        val accountName: String?,
    ) : DisplayedRecordRule() {
        override val sortCategory: Int = 2
    }
}

sealed class Message {
    data object RuleAdded : Message()

    data object RuleExists : Message()
}

class RecordRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(getApplication())

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _rules = MutableStateFlow<List<DisplayedRecordRule>>(emptyList())
    val rules = _rules.asStateFlow()

    private val rulesMutex = Mutex()

    init {
        refreshRules()
    }

    fun acknowledgeFirstMessage() {
        _messages.update { it.drop(1) }
    }

    private fun refreshRules() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    val rawRules = prefs.recordRules ?: Preferences.DEFAULT_RECORD_RULES
                    val displayRules = rawRules.map { rule ->
                        when (rule) {
                            is RecordRule.AllCalls -> DisplayedRecordRule.AllCalls(rule.record)
                            is RecordRule.UnknownCalls -> DisplayedRecordRule.UnknownCalls(rule.record)
                            is RecordRule.Contact -> DisplayedRecordRule.Contact(
                                rule.lookupKey,
                                rule.record,
                                getContactDisplayName(rule.lookupKey),
                            )
                            is RecordRule.ContactGroup -> {
                                val group = getContactGroup(rule.rowId, rule.sourceId)

                                DisplayedRecordRule.ContactGroup(
                                    rule.rowId,
                                    rule.sourceId,
                                    rule.record,
                                    group?.title,
                                    group?.accountName,
                                )
                            }
                        }
                    }

                    // Update and re-save the rules since the display name may have changed,
                    // resulting in a new sort order.
                    saveRulesLocked(displayRules)
                }
            }
        }
    }

    private fun saveRulesLocked(newRules: List<DisplayedRecordRule>) {
        val sortedRules = newRules.sorted()

        _rules.update { sortedRules }

        val rawRules = sortedRules.map { displayedRule ->
            when (displayedRule) {
                is DisplayedRecordRule.AllCalls -> RecordRule.AllCalls(displayedRule.record)
                is DisplayedRecordRule.UnknownCalls -> RecordRule.UnknownCalls(displayedRule.record)
                is DisplayedRecordRule.Contact -> RecordRule.Contact(
                    displayedRule.lookupKey,
                    displayedRule.record,
                )
                is DisplayedRecordRule.ContactGroup -> RecordRule.ContactGroup(
                    displayedRule.rowId,
                    displayedRule.sourceId,
                    displayedRule.record,
                )
            }
        }

        if (rawRules == Preferences.DEFAULT_RECORD_RULES) {
            Log.d(TAG, "New rules match defaults; clearing explicit settings")
            prefs.recordRules = null
        } else {
            prefs.recordRules = rawRules
        }
    }

    private fun getContactDisplayName(lookupKey: String): String? {
        if (getApplication<Application>().checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            getContactByLookupKey(getApplication(), lookupKey)?.displayName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up contact", e)
            null
        }
    }

    private fun getContactGroup(rowId: Long, sourceId: String?): ContactGroupInfo? {
        if (getApplication<Application>().checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val groupLookup = if (sourceId != null) {
            GroupLookup.SourceId(sourceId)
        } else {
            GroupLookup.RowId(rowId)
        }

        return try {
            getContactGroupById(getApplication(), groupLookup)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up contact group", e)
            null
        }
    }

    fun addContactRule(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val contact = try {
                    withContactsByUri(getApplication(), uri) { it.firstOrNull() }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query contact at $uri", e)
                    return@withContext
                }
                if (contact == null) {
                    Log.w(TAG, "Contact not found at $uri")
                    return@withContext
                }

                rulesMutex.withLock {
                    val oldRules = rules.value
                    val existingRule = oldRules.find {
                        it is DisplayedRecordRule.Contact && it.lookupKey == contact.lookupKey
                    }

                    if (existingRule != null) {
                        Log.d(TAG, "Rule already exists for ${contact.lookupKey}")

                        _messages.update { it + Message.RuleExists }
                    } else {
                        Log.d(TAG, "Adding new rule for ${contact.lookupKey}")

                        val newRules = ArrayList(oldRules)
                        newRules.add(
                            DisplayedRecordRule.Contact(
                                contact.lookupKey,
                                true,
                                contact.displayName,
                            )
                        )

                        saveRulesLocked(newRules)

                        _messages.update { it + Message.RuleAdded }
                    }
                }
            }
        }
    }

    fun addContactGroupRule(group: ContactGroupInfo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    val oldRules = rules.value
                    val existingRule = oldRules.find {
                        it is DisplayedRecordRule.ContactGroup &&
                                (it.rowId == group.rowId || it.sourceId == group.sourceId)
                    }

                    if (existingRule != null) {
                        Log.d(TAG, "Rule already exists for ${group.rowId}, ${group.sourceId}")

                        _messages.update { it + Message.RuleExists }
                    } else {
                        Log.d(TAG, "Adding new rule for ${group.rowId}, ${group.sourceId}")

                        val newRules = ArrayList(oldRules)
                        newRules.add(
                            DisplayedRecordRule.ContactGroup(
                                group.rowId,
                                group.sourceId,
                                true,
                                group.title,
                                group.accountName,
                            )
                        )

                        saveRulesLocked(newRules)

                        _messages.update { it + Message.RuleAdded }
                    }
                }
            }
        }
    }

    fun setRuleRecord(index: Int, record: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    saveRulesLocked(rules.value.mapIndexed { i, displayedRule ->
                        if (i == index) {
                            when (displayedRule) {
                                is DisplayedRecordRule.AllCalls -> displayedRule.copy(record = record)
                                is DisplayedRecordRule.UnknownCalls -> displayedRule.copy(record = record)
                                is DisplayedRecordRule.Contact -> displayedRule.copy(record = record)
                                is DisplayedRecordRule.ContactGroup -> displayedRule.copy(record = record)
                            }
                        } else {
                            displayedRule
                        }
                    })
                }
            }
        }
    }

    fun deleteRule(index: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    saveRulesLocked(rules.value.filterIndexed { i, _ -> i != index })
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    prefs.recordRules = null
                    refreshRules()
                }
            }
        }
    }

    companion object {
        private val TAG = RecordRulesViewModel::class.java.simpleName
    }
}
