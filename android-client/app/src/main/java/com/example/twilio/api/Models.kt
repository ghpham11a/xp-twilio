package com.example.twilio.api

import com.google.gson.annotations.SerializedName

// Request models
data class ChatTokenRequest(
    val identity: String
)

data class CreateConversationRequest(
    @SerializedName("friendly_name")
    val friendlyName: String?
)

data class JoinConversationRequest(
    @SerializedName("conversation_sid")
    val conversationSid: String,
    val identity: String
)

data class JoinConversationByNameRequest(
    @SerializedName("conversation_name")
    val conversationName: String,
    val identity: String
)

data class VideoTokenRequest(
    val identity: String,
    @SerializedName("room_name")
    val roomName: String
)

data class CreateRoomRequest(
    @SerializedName("room_name")
    val roomName: String,
    @SerializedName("room_type")
    val roomType: String = "group"
)

// Response models
data class ChatTokenResponse(
    val token: String,
    val identity: String
)

data class VideoTokenResponse(
    val token: String,
    val identity: String,
    @SerializedName("room_name")
    val roomName: String
)

data class Conversation(
    val sid: String,
    @SerializedName("friendly_name")
    val friendlyName: String?,
    @SerializedName("date_created")
    val dateCreated: String?,
    val state: String
)

data class ConversationsResponse(
    val conversations: List<Conversation>
)

data class Room(
    val sid: String,
    @SerializedName("unique_name")
    val uniqueName: String,
    val status: String,
    val type: String,
    @SerializedName("date_created")
    val dateCreated: String?,
    val duration: Int?
)

data class RoomsResponse(
    val rooms: List<Room>
)

data class MessageResponse(
    val message: String
)

data class ParticipantResponse(
    @SerializedName("participant_sid")
    val participantSid: String,
    val identity: String
)
