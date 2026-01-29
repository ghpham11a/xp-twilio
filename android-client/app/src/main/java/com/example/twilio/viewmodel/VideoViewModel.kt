package com.example.twilio.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twilio.api.ApiClient
import com.example.twilio.api.CreateRoomRequest
import com.example.twilio.api.Room as ApiRoom
import com.example.twilio.api.VideoTokenRequest
import com.twilio.video.Camera2Capturer
import com.twilio.video.ConnectOptions
import com.twilio.video.LocalAudioTrack
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoCapturer
import com.twilio.video.RemoteAudioTrack
import com.twilio.video.RemoteAudioTrackPublication
import com.twilio.video.RemoteDataTrack
import com.twilio.video.RemoteDataTrackPublication
import com.twilio.video.RemoteParticipant
import com.twilio.video.RemoteVideoTrack
import com.twilio.video.RemoteVideoTrackPublication
import com.twilio.video.Room
import com.twilio.video.TwilioException
import com.twilio.video.Video
import com.twilio.video.VideoTextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tvi.webrtc.Camera2Enumerator
import java.util.UUID

sealed class VideoUiState {
    data class Lobby(
        val identity: String = "",
        val roomName: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val availableRooms: List<ApiRoom> = emptyList()
    ) : VideoUiState()

    data class Connected(
        val roomName: String,
        val identity: String,
        val participantCount: Int = 1,
        val isAudioEnabled: Boolean = true,
        val isVideoEnabled: Boolean = true,
        val localVideoView: VideoTextureView? = null,
        val remoteVideoViews: Map<String, VideoTextureView> = emptyMap(),
        val hasRemoteParticipants: Boolean = false
    ) : VideoUiState()
}

class VideoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Lobby())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var pendingAction: (() -> Unit)? = null

    // Twilio Video SDK objects
    private var room: Room? = null
    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var cameraCapturer: VideoCapturer? = null
    private var localVideoView: VideoTextureView? = null
    private val remoteVideoViews = mutableMapOf<String, VideoTextureView>()

    companion object {
        private const val TAG = "VideoViewModel"
    }

    // Room listener for room events
    private val roomListener = object : Room.Listener {
        override fun onConnected(room: Room) {
            Log.d(TAG, "Connected to room: ${room.name}")
            val participantCount = 1 + room.remoteParticipants.size

            _uiState.value = VideoUiState.Connected(
                roomName = room.name ?: "",
                identity = room.localParticipant?.identity ?: "",
                participantCount = participantCount,
                isAudioEnabled = localAudioTrack?.isEnabled ?: true,
                isVideoEnabled = localVideoTrack?.isEnabled ?: true,
                localVideoView = localVideoView,
                remoteVideoViews = remoteVideoViews.toMap(),
                hasRemoteParticipants = room.remoteParticipants.isNotEmpty()
            )

            // Set up listeners for existing remote participants
            room.remoteParticipants.forEach { participant ->
                participant.setListener(remoteParticipantListener)
                // Subscribe to any existing video tracks
                participant.remoteVideoTracks.forEach { publication ->
                    publication.remoteVideoTrack?.let { track ->
                        addRemoteVideoTrack(participant.identity ?: participant.sid, track)
                    }
                }
            }
        }

        override fun onConnectFailure(room: Room, twilioException: TwilioException) {
            Log.e(TAG, "Failed to connect to room: ${twilioException.message}")
            cleanupTracks()
            _uiState.value = VideoUiState.Lobby(
                error = "Failed to connect: ${twilioException.message}"
            )
        }

        override fun onReconnecting(room: Room, twilioException: TwilioException) {
            Log.d(TAG, "Reconnecting to room: ${twilioException.message}")
        }

        override fun onReconnected(room: Room) {
            Log.d(TAG, "Reconnected to room: ${room.name}")
        }

        override fun onDisconnected(room: Room, twilioException: TwilioException?) {
            Log.d(TAG, "Disconnected from room: ${room.name}, error: ${twilioException?.message}")
            cleanupTracks()
            _uiState.value = VideoUiState.Lobby()
        }

        override fun onParticipantConnected(room: Room, participant: RemoteParticipant) {
            Log.d(TAG, "Participant connected: ${participant.identity}")
            participant.setListener(remoteParticipantListener)
            updateParticipantCount()
        }

        override fun onParticipantDisconnected(room: Room, participant: RemoteParticipant) {
            Log.d(TAG, "Participant disconnected: ${participant.identity}")
            removeRemoteVideoTrack(participant.identity ?: participant.sid)
            updateParticipantCount()
        }

        override fun onRecordingStarted(room: Room) {
            Log.d(TAG, "Recording started")
        }

        override fun onRecordingStopped(room: Room) {
            Log.d(TAG, "Recording stopped")
        }

        override fun onDominantSpeakerChanged(room: Room, participant: RemoteParticipant?) {
            Log.d(TAG, "Dominant speaker changed: ${participant?.identity}")
        }
    }

    // Remote participant listener for track subscription events
    private val remoteParticipantListener = object : RemoteParticipant.Listener {
        override fun onAudioTrackPublished(participant: RemoteParticipant, publication: RemoteAudioTrackPublication) {
            Log.d(TAG, "Audio track published: ${participant.identity}")
        }

        override fun onAudioTrackUnpublished(participant: RemoteParticipant, publication: RemoteAudioTrackPublication) {
            Log.d(TAG, "Audio track unpublished: ${participant.identity}")
        }

        override fun onVideoTrackPublished(participant: RemoteParticipant, publication: RemoteVideoTrackPublication) {
            Log.d(TAG, "Video track published: ${participant.identity}")
        }

        override fun onVideoTrackUnpublished(participant: RemoteParticipant, publication: RemoteVideoTrackPublication) {
            Log.d(TAG, "Video track unpublished: ${participant.identity}")
        }

        override fun onAudioTrackSubscribed(participant: RemoteParticipant, publication: RemoteAudioTrackPublication, track: RemoteAudioTrack) {
            Log.d(TAG, "Audio track subscribed: ${participant.identity}")
        }

        override fun onAudioTrackUnsubscribed(participant: RemoteParticipant, publication: RemoteAudioTrackPublication, track: RemoteAudioTrack) {
            Log.d(TAG, "Audio track unsubscribed: ${participant.identity}")
        }

        override fun onAudioTrackSubscriptionFailed(participant: RemoteParticipant, publication: RemoteAudioTrackPublication, twilioException: TwilioException) {
            Log.e(TAG, "Audio track subscription failed: ${twilioException.message}")
        }

        override fun onVideoTrackSubscribed(participant: RemoteParticipant, publication: RemoteVideoTrackPublication, track: RemoteVideoTrack) {
            Log.d(TAG, "Video track subscribed: ${participant.identity}")
            addRemoteVideoTrack(participant.identity ?: participant.sid, track)
        }

        override fun onVideoTrackUnsubscribed(participant: RemoteParticipant, publication: RemoteVideoTrackPublication, track: RemoteVideoTrack) {
            Log.d(TAG, "Video track unsubscribed: ${participant.identity}")
            removeRemoteVideoTrack(participant.identity ?: participant.sid)
        }

        override fun onVideoTrackSubscriptionFailed(participant: RemoteParticipant, publication: RemoteVideoTrackPublication, twilioException: TwilioException) {
            Log.e(TAG, "Video track subscription failed: ${twilioException.message}")
        }

        override fun onDataTrackPublished(participant: RemoteParticipant, publication: RemoteDataTrackPublication) {
            Log.d(TAG, "Data track published: ${participant.identity}")
        }

        override fun onDataTrackUnpublished(participant: RemoteParticipant, publication: RemoteDataTrackPublication) {
            Log.d(TAG, "Data track unpublished: ${participant.identity}")
        }

        override fun onDataTrackSubscribed(participant: RemoteParticipant, publication: RemoteDataTrackPublication, track: RemoteDataTrack) {
            Log.d(TAG, "Data track subscribed: ${participant.identity}")
        }

        override fun onDataTrackUnsubscribed(participant: RemoteParticipant, publication: RemoteDataTrackPublication, track: RemoteDataTrack) {
            Log.d(TAG, "Data track unsubscribed: ${participant.identity}")
        }

        override fun onDataTrackSubscriptionFailed(participant: RemoteParticipant, publication: RemoteDataTrackPublication, twilioException: TwilioException) {
            Log.e(TAG, "Data track subscription failed: ${twilioException.message}")
        }

        override fun onAudioTrackEnabled(participant: RemoteParticipant, publication: RemoteAudioTrackPublication) {
            Log.d(TAG, "Audio track enabled: ${participant.identity}")
        }

        override fun onAudioTrackDisabled(participant: RemoteParticipant, publication: RemoteAudioTrackPublication) {
            Log.d(TAG, "Audio track disabled: ${participant.identity}")
        }

        override fun onVideoTrackEnabled(participant: RemoteParticipant, publication: RemoteVideoTrackPublication) {
            Log.d(TAG, "Video track enabled: ${participant.identity}")
        }

        override fun onVideoTrackDisabled(participant: RemoteParticipant, publication: RemoteVideoTrackPublication) {
            Log.d(TAG, "Video track disabled: ${participant.identity}")
        }

        override fun onNetworkQualityLevelChanged(participant: RemoteParticipant, networkQualityLevel: com.twilio.video.NetworkQualityLevel) {
            Log.d(TAG, "Network quality changed for ${participant.identity}: $networkQualityLevel")
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun updateIdentity(identity: String) {
        val current = _uiState.value
        if (current is VideoUiState.Lobby) {
            _uiState.value = current.copy(identity = identity, error = null)
        }
    }

    fun updateRoomName(name: String) {
        val current = _uiState.value
        if (current is VideoUiState.Lobby) {
            _uiState.value = current.copy(roomName = name, error = null)
        }
    }

    fun setPendingAction(action: () -> Unit) {
        pendingAction = action
    }

    fun onPermissionsGranted() {
        pendingAction?.invoke()
        pendingAction = null
    }

    fun onPermissionsDenied() {
        val current = _uiState.value
        if (current is VideoUiState.Lobby) {
            _uiState.value = current.copy(
                isLoading = false,
                error = "Camera/microphone access denied. Please grant permissions in Settings."
            )
        }
        pendingAction = null
    }

    fun loadRooms() {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.listRooms("in-progress")
                val current = _uiState.value
                if (current is VideoUiState.Lobby) {
                    _uiState.value = current.copy(availableRooms = response.rooms)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load rooms", e)
                // Silently fail - rooms list is not critical
            }
        }
    }

    fun createRoom() {
        val current = _uiState.value
        if (current is VideoUiState.Lobby) {
            if (current.identity.isBlank()) {
                _uiState.value = current.copy(error = "Please enter your name")
                return
            }
            val uuid = UUID.randomUUID().toString()
            connectToRoom(current.identity, uuid)
        }
    }

    fun joinRoom() {
        val current = _uiState.value
        if (current is VideoUiState.Lobby) {
            if (current.identity.isBlank()) {
                _uiState.value = current.copy(error = "Please enter your name")
                return
            }
            if (current.roomName.isBlank()) {
                _uiState.value = current.copy(error = "Please enter a room name")
                return
            }
            connectToRoom(current.identity, current.roomName)
        }
    }

    private fun createLocalTracks(): Boolean {
        val context = appContext ?: return false

        try {
            // Create local audio track
            localAudioTrack = LocalAudioTrack.create(context, true)
            Log.d(TAG, "Created local audio track")

            // Create camera capturer using front camera (Camera2 API)
            val camera2Enumerator = Camera2Enumerator(context)
            val frontCameraId = camera2Enumerator.deviceNames.firstOrNull { cameraId ->
                camera2Enumerator.isFrontFacing(cameraId)
            }

            if (frontCameraId != null) {
                cameraCapturer = Camera2Capturer(context, frontCameraId, null)
                localVideoTrack = LocalVideoTrack.create(context, true, cameraCapturer!!)
                Log.d(TAG, "Created local video track with front camera: $frontCameraId")

                // Create video view for local preview
                localVideoView = VideoTextureView(context).apply {
                    mirror = true // Mirror front camera
                }
                localVideoTrack?.addSink(localVideoView!!)
            } else {
                Log.w(TAG, "No front camera available")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create local tracks", e)
            cleanupTracks()
            return false
        }
    }

    private fun connectToRoom(identity: String, roomName: String) {
        val context = appContext ?: return
        val current = _uiState.value
        if (current is VideoUiState.Lobby) {
            _uiState.value = current.copy(isLoading = true, error = null)
        }

        viewModelScope.launch {
            try {
                // Create local tracks first
                if (!createLocalTracks()) {
                    handleError("Failed to create camera/microphone")
                    return@launch
                }

                // Create room on backend (may already exist)
                try {
                    ApiClient.apiService.createRoom(CreateRoomRequest(roomName))
                    Log.d(TAG, "Created room: $roomName")
                } catch (e: Exception) {
                    Log.d(TAG, "Room might already exist: $roomName")
                    // Room might already exist, continue
                }

                // Get access token
                val tokenResponse = ApiClient.apiService.getVideoToken(
                    VideoTokenRequest(identity, roomName)
                )
                Log.d(TAG, "Got video token for room: ${tokenResponse.roomName}")

                // Build connect options
                val connectOptionsBuilder = ConnectOptions.Builder(tokenResponse.token)
                    .roomName(roomName)

                localAudioTrack?.let {
                    connectOptionsBuilder.audioTracks(listOf(it))
                }
                localVideoTrack?.let {
                    connectOptionsBuilder.videoTracks(listOf(it))
                }

                val connectOptions = connectOptionsBuilder.build()

                // Connect to the room
                room = Video.connect(context, connectOptions, roomListener)
                Log.d(TAG, "Connecting to room: $roomName")

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to room", e)
                cleanupTracks()
                handleError(e.message ?: "Failed to connect to room")
            }
        }
    }

    private fun addRemoteVideoTrack(participantIdentity: String, track: RemoteVideoTrack) {
        val context = appContext ?: return

        // Create video view for remote participant
        val videoView = VideoTextureView(context)
        track.addSink(videoView)
        remoteVideoViews[participantIdentity] = videoView

        updateUiState()
    }

    private fun removeRemoteVideoTrack(participantIdentity: String) {
        remoteVideoViews.remove(participantIdentity)
        updateUiState()
    }

    private fun updateParticipantCount() {
        val currentRoom = room ?: return
        updateUiState()
    }

    private fun updateUiState() {
        val currentRoom = room ?: return
        val current = _uiState.value
        if (current is VideoUiState.Connected) {
            _uiState.value = current.copy(
                participantCount = 1 + currentRoom.remoteParticipants.size,
                localVideoView = localVideoView,
                remoteVideoViews = remoteVideoViews.toMap(),
                hasRemoteParticipants = currentRoom.remoteParticipants.isNotEmpty()
            )
        }
    }

    fun toggleAudio() {
        val current = _uiState.value
        if (current is VideoUiState.Connected) {
            val newState = !current.isAudioEnabled
            localAudioTrack?.enable(newState)
            _uiState.value = current.copy(isAudioEnabled = newState)
            Log.d(TAG, "Audio ${if (newState) "enabled" else "disabled"}")
        }
    }

    fun toggleVideo() {
        val current = _uiState.value
        if (current is VideoUiState.Connected) {
            val newState = !current.isVideoEnabled
            localVideoTrack?.enable(newState)
            _uiState.value = current.copy(isVideoEnabled = newState)
            Log.d(TAG, "Video ${if (newState) "enabled" else "disabled"}")
        }
    }

    fun leaveRoom() {
        room?.disconnect()
        cleanupTracks()
        _uiState.value = VideoUiState.Lobby()
        Log.d(TAG, "Left room")
    }

    private fun cleanupTracks() {
        // Remove sinks from local video track
        localVideoView?.let { view ->
            localVideoTrack?.removeSink(view)
        }

        // Release tracks
        localAudioTrack?.release()
        localAudioTrack = null

        localVideoTrack?.release()
        localVideoTrack = null

        cameraCapturer = null
        localVideoView = null

        // Clear remote views
        remoteVideoViews.clear()

        room = null
    }

    private fun handleError(message: String) {
        val current = _uiState.value
        when (current) {
            is VideoUiState.Lobby -> _uiState.value = current.copy(isLoading = false, error = message)
            is VideoUiState.Connected -> leaveRoom()
        }
    }

    override fun onCleared() {
        super.onCleared()
        room?.disconnect()
        cleanupTracks()
    }
}
