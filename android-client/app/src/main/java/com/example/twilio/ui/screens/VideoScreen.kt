package com.example.twilio.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.twilio.api.Room as ApiRoom
import com.example.twilio.viewmodel.VideoUiState
import com.example.twilio.viewmodel.VideoViewModel
import com.twilio.video.VideoTextureView

@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    // Check and request permissions
    fun checkAndRequestPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        )
        val audioPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        )

        return if (cameraPermission == PackageManager.PERMISSION_GRANTED &&
            audioPermission == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
            false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadRooms()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is VideoUiState.Lobby -> VideoLobby(
                identity = state.identity,
                roomName = state.roomName,
                isLoading = state.isLoading,
                error = state.error,
                availableRooms = state.availableRooms,
                onIdentityChange = viewModel::updateIdentity,
                onRoomNameChange = viewModel::updateRoomName,
                onCreateRoom = {
                    if (checkAndRequestPermissions()) {
                        viewModel.createRoom()
                    } else {
                        viewModel.setPendingAction { viewModel.createRoom() }
                    }
                },
                onJoinRoom = {
                    if (checkAndRequestPermissions()) {
                        viewModel.joinRoom()
                    } else {
                        viewModel.setPendingAction { viewModel.joinRoom() }
                    }
                },
                onRefreshRooms = viewModel::loadRooms,
                onRoomSelected = viewModel::updateRoomName
            )
            is VideoUiState.Connected -> VideoRoom(
                roomName = state.roomName,
                identity = state.identity,
                participantCount = state.participantCount,
                isAudioEnabled = state.isAudioEnabled,
                isVideoEnabled = state.isVideoEnabled,
                localVideoView = state.localVideoView,
                remoteVideoViews = state.remoteVideoViews,
                hasRemoteParticipants = state.hasRemoteParticipants,
                onToggleAudio = viewModel::toggleAudio,
                onToggleVideo = viewModel::toggleVideo,
                onLeaveRoom = viewModel::leaveRoom
            )
        }
    }
}

@Composable
private fun VideoLobby(
    identity: String,
    roomName: String,
    isLoading: Boolean,
    error: String?,
    availableRooms: List<ApiRoom>,
    onIdentityChange: (String) -> Unit,
    onRoomNameChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onRefreshRooms: () -> Unit,
    onRoomSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Video Conference",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = identity,
                onValueChange = onIdentityChange,
                label = { Text("Your name") },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create new room",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Button(
                onClick = onCreateRoom,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Creating..." else "Create Room")
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Or join existing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = roomName,
                onValueChange = onRoomNameChange,
                label = { Text("Room name") },
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onJoinRoom() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = onJoinRoom,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Joining..." else "Join Room")
            }
        }

        if (error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (availableRooms.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Rooms",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onRefreshRooms) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            items(availableRooms, key = { it.sid }) { room ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRoomSelected(room.uniqueName) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = room.uniqueName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = room.type,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VideoRoom(
    roomName: String,
    identity: String,
    participantCount: Int,
    isAudioEnabled: Boolean,
    isVideoEnabled: Boolean,
    localVideoView: VideoTextureView?,
    remoteVideoViews: Map<String, VideoTextureView>,
    hasRemoteParticipants: Boolean,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Room: $roomName",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$participantCount participant(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Video grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            // Remote video (main area)
            if (remoteVideoViews.isNotEmpty()) {
                // Show first remote participant's video as main
                val firstRemote = remoteVideoViews.entries.firstOrNull()
                if (firstRemote != null) {
                    key(firstRemote.key) {
                        VideoTextureViewComposable(
                            videoView = firstRemote.value,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (!hasRemoteParticipants) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Waiting for others to join...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Local video (picture-in-picture)
            if (localVideoView != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(width = 120.dp, height = 160.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box {
                        VideoTextureViewComposable(
                            videoView = localVideoView,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "You ($identity)",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }

            // Additional remote participants (if more than one)
            if (remoteVideoViews.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    remoteVideoViews.entries.drop(1).take(3).forEach { (participantId, videoView) ->
                        Card(
                            modifier = Modifier.size(width = 100.dp, height = 133.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box {
                                key(participantId) {
                                    VideoTextureViewComposable(
                                        videoView = videoView,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = participantId.take(10),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Controls
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onToggleAudio,
                    colors = if (!isAudioEnabled) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isAudioEnabled) "Mute" else "Unmute")
                }

                Button(
                    onClick = onToggleVideo,
                    colors = if (!isVideoEnabled) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isVideoEnabled) "Stop Video" else "Start Video")
                }

                Button(
                    onClick = onLeaveRoom,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Leave")
                }
            }
        }
    }
}

/**
 * Composable wrapper for VideoTextureView that properly handles view lifecycle.
 * VideoTextureView can only have one parent at a time, so we need to remove it
 * from any existing parent before attaching to a new one.
 */
@Composable
private fun VideoTextureViewComposable(
    videoView: VideoTextureView,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { _ ->
            // Remove from existing parent if any
            (videoView.parent as? ViewGroup)?.removeView(videoView)
            videoView
        },
        update = { view ->
            // Remove from existing parent if any (in case of recomposition)
            (view.parent as? ViewGroup)?.let { parent ->
                if (parent != view.parent) {
                    parent.removeView(view)
                }
            }
        },
        modifier = modifier
    )
}
