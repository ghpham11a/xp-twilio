package com.example.twilio.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twilio.api.ApiClient
import com.example.twilio.api.ChatTokenRequest
import com.example.twilio.api.JoinConversationByNameRequest
import com.twilio.conversations.CallbackListener
import com.twilio.conversations.Conversation
import com.twilio.conversations.ConversationListener
import com.twilio.conversations.ConversationsClient
import com.twilio.conversations.ConversationsClientListener
import com.twilio.conversations.Message
import com.twilio.conversations.Participant
import com.twilio.conversations.User
import com.twilio.util.ErrorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val sid: String,
    val author: String,
    val body: String,
    val dateCreated: String?
)

sealed class ChatUiState {
    data class Lobby(
        val identity: String = "",
        val conversationName: String = "",
        val isLoading: Boolean = false,
        val error: String? = null
    ) : ChatUiState()

    data class Connected(
        val conversationName: String,
        val identity: String,
        val messages: List<ChatMessage> = emptyList(),
        val newMessage: String = "",
        val error: String? = null
    ) : ChatUiState()
}

class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Lobby())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var currentIdentity: String = ""
    private var currentConversationSid: String = ""

    // Twilio SDK objects
    private var conversationsClient: ConversationsClient? = null
    private var currentConversation: Conversation? = null

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // Listener for conversation events (real-time message updates)
    private val conversationListener = object : ConversationListener {
        override fun onMessageAdded(message: Message) {
            Log.d(TAG, "Message added: ${message.body} from ${message.author}")
            val chatMessage = ChatMessage(
                sid = message.sid,
                author = message.author ?: "Unknown",
                body = message.body ?: "",
                dateCreated = message.dateCreated
            )
            val current = _uiState.value
            if (current is ChatUiState.Connected) {
                // Avoid duplicates (in case we already added it optimistically)
                if (current.messages.none { it.sid == chatMessage.sid }) {
                    _uiState.value = current.copy(
                        messages = current.messages + chatMessage
                    )
                }
            }
        }

        override fun onMessageUpdated(message: Message, reason: Message.UpdateReason) {
            Log.d(TAG, "Message updated: ${message.sid}")
        }

        override fun onMessageDeleted(message: Message) {
            Log.d(TAG, "Message deleted: ${message.sid}")
            val current = _uiState.value
            if (current is ChatUiState.Connected) {
                _uiState.value = current.copy(
                    messages = current.messages.filter { it.sid != message.sid }
                )
            }
        }

        override fun onParticipantAdded(participant: Participant) {
            Log.d(TAG, "Participant added: ${participant.identity}")
        }

        override fun onParticipantUpdated(participant: Participant, reason: Participant.UpdateReason) {
            Log.d(TAG, "Participant updated: ${participant.identity}")
        }

        override fun onParticipantDeleted(participant: Participant) {
            Log.d(TAG, "Participant deleted: ${participant.identity}")
        }

        override fun onTypingStarted(conversation: Conversation, participant: Participant) {
            Log.d(TAG, "Typing started: ${participant.identity}")
        }

        override fun onTypingEnded(conversation: Conversation, participant: Participant) {
            Log.d(TAG, "Typing ended: ${participant.identity}")
        }

        override fun onSynchronizationChanged(conversation: Conversation) {
            Log.d(TAG, "Conversation synchronization changed")
        }
    }

    // Listener for client-level events
    private val clientListener = object : ConversationsClientListener {
        override fun onConversationAdded(conversation: Conversation) {
            Log.d(TAG, "Conversation added: ${conversation.sid}")
        }

        override fun onConversationUpdated(conversation: Conversation, reason: Conversation.UpdateReason) {
            Log.d(TAG, "Conversation updated: ${conversation.sid}")
        }

        override fun onConversationDeleted(conversation: Conversation) {
            Log.d(TAG, "Conversation deleted: ${conversation.sid}")
        }

        override fun onConversationSynchronizationChange(conversation: Conversation) {
            Log.d(TAG, "Conversation sync change: ${conversation.sid}")
        }

        override fun onError(errorInfo: ErrorInfo) {
            Log.e(TAG, "Client error: ${errorInfo.message}")
            handleError(errorInfo.message)
        }

        override fun onUserSubscribed(user: User) {
            Log.d(TAG, "User subscribed: ${user.identity}")
        }

        override fun onUserUnsubscribed(user: User) {
            Log.d(TAG, "User unsubscribed: ${user.identity}")
        }

        override fun onUserUpdated(user: User, reason: User.UpdateReason) {
            Log.d(TAG, "User updated: ${user.identity}")
        }

        override fun onClientSynchronization(status: ConversationsClient.SynchronizationStatus) {
            Log.d(TAG, "Client synchronization status: $status")
        }

        override fun onNewMessageNotification(conversationSid: String, messageSid: String, messageIndex: Long) {
            Log.d(TAG, "New message notification: $messageSid in $conversationSid")
        }

        override fun onAddedToConversationNotification(conversationSid: String) {
            Log.d(TAG, "Added to conversation notification: $conversationSid")
        }

        override fun onRemovedFromConversationNotification(conversationSid: String) {
            Log.d(TAG, "Removed from conversation notification: $conversationSid")
        }

        override fun onConnectionStateChange(state: ConversationsClient.ConnectionState) {
            Log.d(TAG, "Connection state changed: $state")
        }

        override fun onTokenAboutToExpire() {
            Log.d(TAG, "Token about to expire - should refresh")
            // TODO: Implement token refresh
        }

        override fun onTokenExpired() {
            Log.e(TAG, "Token expired")
            handleError("Session expired. Please reconnect.")
        }

        override fun onNotificationSubscribed() {
            Log.d(TAG, "Notification subscribed")
        }

        override fun onNotificationFailed(errorInfo: ErrorInfo?) {
            Log.e(TAG, "onNotificationFailed: ${errorInfo?.message}")
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun updateIdentity(identity: String) {
        val current = _uiState.value
        if (current is ChatUiState.Lobby) {
            _uiState.value = current.copy(identity = identity, error = null)
        }
    }

    fun updateConversationName(name: String) {
        val current = _uiState.value
        if (current is ChatUiState.Lobby) {
            _uiState.value = current.copy(conversationName = name, error = null)
        }
    }

    fun updateNewMessage(message: String) {
        val current = _uiState.value
        if (current is ChatUiState.Connected) {
            _uiState.value = current.copy(newMessage = message, error = null)
        }
    }

    fun createChat() {
        val current = _uiState.value
        if (current is ChatUiState.Lobby) {
            if (current.identity.isBlank()) {
                _uiState.value = current.copy(error = "Please enter your name")
                return
            }
            val uuid = UUID.randomUUID().toString()
            connectToChat(current.identity, uuid)
        }
    }

    fun joinChat() {
        val current = _uiState.value
        if (current is ChatUiState.Lobby) {
            if (current.identity.isBlank()) {
                _uiState.value = current.copy(error = "Please enter your name")
                return
            }
            if (current.conversationName.isBlank()) {
                _uiState.value = current.copy(error = "Please enter a conversation name")
                return
            }
            connectToChat(current.identity, current.conversationName)
        }
    }

    private fun connectToChat(identity: String, chatName: String) {
        val context = appContext ?: return
        val current = _uiState.value
        if (current is ChatUiState.Lobby) {
            _uiState.value = current.copy(isLoading = true, error = null)
        }

        viewModelScope.launch {
            try {
                // Get token from backend
                val tokenResponse = ApiClient.apiService.getChatToken(
                    ChatTokenRequest(identity)
                )
                Log.d(TAG, "Got chat token for identity: ${tokenResponse.identity}")

                // Join or create conversation via backend
                val convData = ApiClient.apiService.joinConversationByName(
                    JoinConversationByNameRequest(chatName, identity)
                )
                Log.d(TAG, "Joined conversation: ${convData.sid}")

                currentIdentity = identity
                currentConversationSid = convData.sid

                // Create Twilio Conversations client
                val properties = ConversationsClient.Properties.newBuilder().createProperties()
                ConversationsClient.create(context, tokenResponse.token, properties, object : CallbackListener<ConversationsClient> {
                    override fun onSuccess(client: ConversationsClient) {
                        Log.d(TAG, "ConversationsClient created successfully")
                        conversationsClient = client
                        client.addListener(clientListener)

                        // Get the conversation by SID
                        client.getConversation(currentConversationSid, object : CallbackListener<Conversation> {
                            override fun onSuccess(conversation: Conversation) {
                                Log.d(TAG, "Got conversation: ${conversation.friendlyName}")
                                currentConversation = conversation
                                conversation.addListener(conversationListener)

                                // Transition to connected state
                                _uiState.value = ChatUiState.Connected(
                                    conversationName = conversation.friendlyName ?: chatName,
                                    identity = identity,
                                    messages = emptyList()
                                )

                                // Load existing messages
                                loadMessages()
                            }

                            override fun onError(errorInfo: ErrorInfo) {
                                Log.e(TAG, "Failed to get conversation: ${errorInfo.message}")
                                handleError("Failed to join conversation: ${errorInfo.message}")
                            }
                        })
                    }

                    override fun onError(errorInfo: ErrorInfo) {
                        Log.e(TAG, "Failed to create ConversationsClient: ${errorInfo.message}")
                        handleError("Failed to connect: ${errorInfo.message}")
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to chat", e)
                handleError(e.message ?: "Failed to connect to chat")
            }
        }
    }

    private fun loadMessages() {
        currentConversation?.getLastMessages(100, object : CallbackListener<List<Message>> {
            override fun onSuccess(messages: List<Message>) {
                Log.d(TAG, "Loaded ${messages.size} messages")
                val chatMessages = messages.map { message ->
                    ChatMessage(
                        sid = message.sid,
                        author = message.author ?: "Unknown",
                        body = message.body ?: "",
                        dateCreated = message.dateCreated
                    )
                }
                val current = _uiState.value
                if (current is ChatUiState.Connected) {
                    _uiState.value = current.copy(messages = chatMessages)
                }
            }

            override fun onError(errorInfo: ErrorInfo) {
                Log.e(TAG, "Failed to load messages: ${errorInfo.message}")
            }
        })
    }

    fun sendMessage() {
        val current = _uiState.value
        if (current is ChatUiState.Connected && current.newMessage.isNotBlank()) {
            val messageText = current.newMessage.trim()
            _uiState.value = current.copy(newMessage = "", error = null)

            // Send via Twilio SDK (v4.x API)
            currentConversation?.prepareMessage()
                ?.setBody(messageText)
                ?.buildAndSend(object : CallbackListener<Message> {
                    override fun onSuccess(message: Message) {
                        Log.d(TAG, "Message sent successfully: ${message.sid}")
                        // Message will be added via onMessageAdded listener
                    }

                    override fun onError(errorInfo: ErrorInfo) {
                        Log.e(TAG, "Failed to send message: ${errorInfo.message}")
                        val currentState = _uiState.value
                        if (currentState is ChatUiState.Connected) {
                            _uiState.value = currentState.copy(error = "Failed to send message")
                        }
                    }
                })
        }
    }

    fun leaveChat() {
        cleanup()
        _uiState.value = ChatUiState.Lobby()
    }

    private fun cleanup() {
        currentConversation?.removeListener(conversationListener)
        currentConversation = null
        conversationsClient?.removeListener(clientListener)
        conversationsClient?.shutdown()
        conversationsClient = null
        currentIdentity = ""
        currentConversationSid = ""
    }

    private fun handleError(message: String) {
        val current = _uiState.value
        when (current) {
            is ChatUiState.Lobby -> _uiState.value = current.copy(isLoading = false, error = message)
            is ChatUiState.Connected -> _uiState.value = current.copy(error = message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
