package com.example.twilio.api

import retrofit2.http.*

interface ApiService {
    // Chat API
    @POST("chat/token")
    suspend fun getChatToken(@Body request: ChatTokenRequest): ChatTokenResponse

    @POST("chat/conversations")
    suspend fun createConversation(@Body request: CreateConversationRequest): Conversation

    @GET("chat/conversations")
    suspend fun listConversations(): ConversationsResponse

    @POST("chat/conversations/join")
    suspend fun joinConversation(@Body request: JoinConversationRequest): ParticipantResponse

    @POST("chat/conversations/join-by-name")
    suspend fun joinConversationByName(@Body request: JoinConversationByNameRequest): Conversation

    @DELETE("chat/conversations/{sid}")
    suspend fun deleteConversation(@Path("sid") conversationSid: String): MessageResponse

    // Video API
    @POST("video/token")
    suspend fun getVideoToken(@Body request: VideoTokenRequest): VideoTokenResponse

    @POST("video/rooms")
    suspend fun createRoom(@Body request: CreateRoomRequest): Room

    @GET("video/rooms")
    suspend fun listRooms(@Query("status") status: String = "in-progress"): RoomsResponse

    @POST("video/rooms/{sid}/end")
    suspend fun endRoom(@Path("sid") roomSid: String): MessageResponse
}
