package com.kunzisoft.keepass.viewmodels

import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kunzisoft.keepass.database.element.Attachment
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.element.node.NodeId
import com.kunzisoft.keepass.model.EntryInfo
import com.kunzisoft.keepass.otp.OtpElement
import java.util.*


class EntryViewModel: ViewModel() {

    private val mDatabase = Database.getInstance()

    val entry : LiveData<EntryHistory> get() = _entry
    private val _entry = MutableLiveData<EntryHistory>()

    val entryInfo : LiveData<EntryInfo> get() = _entryInfo
    private val _entryInfo = MutableLiveData<EntryInfo>()

    val entryHistory : LiveData<List<Entry>> get() = _entryHistory
    private val _entryHistory = MutableLiveData<List<Entry>>()

    val otpElement : LiveData<OtpElement> get() = _otpElement
    private val _otpElement = SingleLiveEvent<OtpElement>()

    val attachmentSelected : LiveData<Attachment> get() = _attachmentSelected
    private val _attachmentSelected = SingleLiveEvent<Attachment>()

    val historySelected : LiveData<EntryHistory> get() = _historySelected
    private val _historySelected = SingleLiveEvent<EntryHistory>()

    fun selectEntry(nodeIdUUID: NodeId<UUID>?, historyPosition: Int) {
        if (nodeIdUUID != null) {
            // Manage current version and history
            val entryLastVersion = mDatabase.getEntryById(nodeIdUUID)
            var entry = entryLastVersion
            if (historyPosition > -1) {
                entry = entry?.getHistory()?.get(historyPosition)
            }
            // To update current modification time
            entry?.touch(modified = false, touchParents = false)
            // To simplify template field visibility
            entry?.let {
               entry = mDatabase.decodeEntryWithTemplateConfiguration(it)
            }
            _entry.value = EntryHistory(nodeIdUUID, entry, entryLastVersion, historyPosition)
            _entryInfo.value = entry?.getEntryInfo(mDatabase)
            _entryHistory.value = entry?.getHistory()
        }
    }

    fun reloadEntry() {
        selectEntry(entry.value?.nodeIdUUID, entry.value?.historyPosition ?: -1)
    }

    fun onOtpElementUpdated(optElement: OtpElement) {
        _otpElement.value = optElement
    }

    fun onAttachmentSelected(attachment: Attachment) {
        _attachmentSelected.value = attachment
    }

    fun onHistorySelected(item: Entry, position: Int) {
        _historySelected.value = EntryHistory(item.nodeId, item, null, position)
    }

    // Custom data class to manage entry to retrieve and define is it's an history item (!= -1)
    data class EntryHistory(var nodeIdUUID: NodeId<UUID>?,
                            var entry: Entry?,
                            var lastEntryVersion: Entry?,
                            var historyPosition: Int = -1): Parcelable {
        constructor(parcel: Parcel) : this(
                parcel.readParcelable(NodeId::class.java.classLoader),
                parcel.readParcelable(Entry::class.java.classLoader),
                parcel.readParcelable(Entry::class.java.classLoader),
                parcel.readInt())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(nodeIdUUID, flags)
            parcel.writeParcelable(entry, flags)
            parcel.writeParcelable(lastEntryVersion, flags)
            parcel.writeInt(historyPosition)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<EntryHistory> {
            override fun createFromParcel(parcel: Parcel): EntryHistory {
                return EntryHistory(parcel)
            }

            override fun newArray(size: Int): Array<EntryHistory?> {
                return arrayOfNulls(size)
            }
        }
    }

    companion object {
        private val TAG = EntryViewModel::class.java.name
    }
}