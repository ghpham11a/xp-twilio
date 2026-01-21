import { useState, useRef, useEffect, useCallback } from "react";
import Video, {
  createLocalTracks,
  type Room,
  type LocalVideoTrack,
  type LocalAudioTrack,
  type LocalTrack,
  type RemoteParticipant,
  type RemoteTrack,
  type RemoteTrackPublication,
} from "twilio-video";
import { getVideoToken, createRoom, listRooms, type Room as RoomData } from "../api";
import "./Video.css";

export function VideoChat() {
  const [identity, setIdentity] = useState("");
  const [roomName, setRoomName] = useState("");
  const [room, setRoom] = useState<Room | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isAudioEnabled, setIsAudioEnabled] = useState(true);
  const [isVideoEnabled, setIsVideoEnabled] = useState(true);
  const [availableRooms, setAvailableRooms] = useState<RoomData[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [localTracks, setLocalTracks] = useState<LocalTrack[]>([]);

  const localVideoRef = useRef<HTMLDivElement>(null);
  const remoteVideoRef = useRef<HTMLDivElement>(null);
  const roomRef = useRef<Room | null>(null);
  const localTracksRef = useRef<LocalTrack[]>([]);

  const loadRooms = async () => {
    try {
      const { rooms } = await listRooms("in-progress");
      setAvailableRooms(rooms);
    } catch (err) {
      console.error("Failed to load rooms:", err);
    }
  };

  useEffect(() => {
    loadRooms();
  }, []);

  // Attach local video track when ref becomes available (after room UI renders)
  useEffect(() => {
    const videoTrack = localTracks.find(track => track.kind === 'video') as LocalVideoTrack | undefined;
    if (videoTrack && localVideoRef.current) {
      // Clear any existing video elements first
      localVideoRef.current.innerHTML = '';
      const element = videoTrack.attach();
      localVideoRef.current.appendChild(element);
    }
  }, [localTracks, room]); // room dependency ensures this runs after room UI renders

  const attachTrack = useCallback((track: RemoteTrack, container: HTMLDivElement) => {
    if (track.kind === "video" || track.kind === "audio") {
      const element = track.attach();
      container.appendChild(element);
    }
  }, []);

  const detachTrack = useCallback((track: RemoteTrack) => {
    if (track.kind === "video" || track.kind === "audio") {
      track.detach().forEach((el) => el.remove());
    }
  }, []);

  const handleParticipantConnected = useCallback(
    (participant: RemoteParticipant) => {
      participant.tracks.forEach((publication) => {
        if (publication.isSubscribed && publication.track && remoteVideoRef.current) {
          attachTrack(publication.track, remoteVideoRef.current);
        }
      });

      participant.on("trackSubscribed", (track) => {
        if (remoteVideoRef.current) {
          attachTrack(track, remoteVideoRef.current);
        }
      });

      participant.on("trackUnsubscribed", (track) => {
        detachTrack(track);
      });
    },
    [attachTrack, detachTrack]
  );

  const handleParticipantDisconnected = useCallback(
    (participant: RemoteParticipant) => {
      participant.tracks.forEach((publication: RemoteTrackPublication) => {
        if (publication.track) {
          detachTrack(publication.track);
        }
      });
    },
    [detachTrack]
  );

  const connectToRoom = async (name: string) => {
    setIsConnecting(true);
    setError(null);

    let localTracks: LocalTrack[] = [];

    try {
      // Create local tracks first (this also handles permissions)
      try {
        localTracks = await createLocalTracks({
          audio: true,
          video: { width: 640 },
        });
      } catch (err) {
        if (err instanceof Error) {
          if (err.name === "NotAllowedError" || err.name === "PermissionDeniedError") {
            setError("Camera/microphone access denied. Please allow permissions in your browser settings and refresh the page.");
          } else if (err.name === "NotFoundError" || err.name === "DevicesNotFoundError") {
            setError("No camera or microphone found. Please connect a device and try again.");
          } else if (err.name === "NotReadableError" || err.name === "TrackStartError") {
            setError("Camera or microphone is already in use by another application.");
          } else {
            setError(`Media access error: ${err.message}`);
          }
        } else {
          setError("Failed to access camera/microphone.");
        }
        setIsConnecting(false);
        return;
      }

      // Store local tracks for attachment and cleanup
      setLocalTracks(localTracks);

      // Create room on backend first (may already exist)
      try {
        await createRoom(name);
      } catch {
        // Room might already exist, continue
      }

      // Get access token
      const { token } = await getVideoToken(identity, name);

      // Connect to room with our pre-created tracks
      const videoRoom = await Video.connect(token, {
        name: name,
        tracks: localTracks,
      });

      setRoom(videoRoom);
      setRoomName(name);

      // Handle existing participants
      videoRoom.participants.forEach(handleParticipantConnected);

      // Handle new participants
      videoRoom.on("participantConnected", handleParticipantConnected);
      videoRoom.on("participantDisconnected", handleParticipantDisconnected);

      videoRoom.on("disconnected", () => {
        setRoom(null);
        if (localVideoRef.current) {
          localVideoRef.current.innerHTML = "";
        }
        if (remoteVideoRef.current) {
          remoteVideoRef.current.innerHTML = "";
        }
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to join room");
    } finally {
      setIsConnecting(false);
    }
  };

  const joinRoom = async () => {
    if (!identity.trim()) {
      setError("Please enter your name");
      return;
    }
    if (!roomName.trim()) {
      setError("Please enter a room name");
      return;
    }
    await connectToRoom(roomName);
  };

  const createNewRoom = async () => {
    if (!identity.trim()) {
      setError("Please enter your name");
      return;
    }
    const uuid = crypto.randomUUID();
    await connectToRoom(uuid);
  };

  const leaveRoom = () => {
    if (room) {
      room.disconnect();
      setRoom(null);
    }
    // Stop all local tracks (audio and video)
    localTracks.forEach(track => track.stop());
    setLocalTracks([]);
  };

  const toggleAudio = () => {
    if (room) {
      room.localParticipant.audioTracks.forEach((publication) => {
        const track = publication.track as LocalAudioTrack;
        if (isAudioEnabled) {
          track.disable();
        } else {
          track.enable();
        }
      });
      setIsAudioEnabled(!isAudioEnabled);
    }
  };

  const toggleVideo = () => {
    if (room) {
      room.localParticipant.videoTracks.forEach((publication) => {
        const track = publication.track as LocalVideoTrack;
        if (isVideoEnabled) {
          track.disable();
        } else {
          track.enable();
        }
      });
      setIsVideoEnabled(!isVideoEnabled);
    }
  };

  // Keep refs in sync with state for cleanup purposes
  useEffect(() => {
    roomRef.current = room;
  }, [room]);

  useEffect(() => {
    localTracksRef.current = localTracks;
  }, [localTracks]);

  // Cleanup on unmount only
  useEffect(() => {
    return () => {
      if (roomRef.current) {
        roomRef.current.disconnect();
      }
      // Stop all local tracks (audio and video)
      localTracksRef.current.forEach(track => track.stop());
    };
  }, []);

  if (!room) {
    return (
      <div className="video-lobby">
        <h2>Video Conference</h2>

        <div className="join-form">
          <input
            type="text"
            placeholder="Your name"
            value={identity}
            onChange={(e) => setIdentity(e.target.value)}
            disabled={isConnecting}
          />

          <div className="divider">
            <span>Create new room</span>
          </div>

          <button onClick={createNewRoom} disabled={isConnecting} className="create-btn">
            {isConnecting ? "Creating..." : "Create Room"}
          </button>

          <div className="divider">
            <span>Or join existing</span>
          </div>

          <input
            type="text"
            placeholder="Room name"
            value={roomName}
            onChange={(e) => setRoomName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && joinRoom()}
            disabled={isConnecting}
          />
          <button onClick={joinRoom} disabled={isConnecting}>
            {isConnecting ? "Joining..." : "Join Room"}
          </button>
        </div>

        {error && <p className="error">{error}</p>}

        {availableRooms.length > 0 && (
          <div className="available-rooms">
            <div className="rooms-header">
              <h3>Active Rooms</h3>
              <button onClick={loadRooms} className="refresh-btn">
                Refresh
              </button>
            </div>

            <div className="room-list">
              {availableRooms.map((r) => (
                <div
                  key={r.sid}
                  className="room-item"
                  onClick={() => setRoomName(r.unique_name)}
                >
                  <span className="room-name">{r.unique_name}</span>
                  <span className="room-type">{r.type}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="video-room">
      <div className="video-header">
        <h3>Room: {room.name}</h3>
        <div className="participant-count">
          {room.participants.size + 1} participant(s)
        </div>
      </div>

      <div className="video-grid">
        <div className="video-container local">
          <div ref={localVideoRef} className="video-element" />
          <span className="video-label">You ({identity})</span>
        </div>

        <div className="video-container remote">
          <div ref={remoteVideoRef} className="video-element" />
          {room.participants.size === 0 && (
            <div className="waiting-message">
              Waiting for others to join...
            </div>
          )}
        </div>
      </div>

      <div className="video-controls">
        <button
          onClick={toggleAudio}
          className={!isAudioEnabled ? "disabled" : ""}
        >
          {isAudioEnabled ? "Mute" : "Unmute"}
        </button>
        <button
          onClick={toggleVideo}
          className={!isVideoEnabled ? "disabled" : ""}
        >
          {isVideoEnabled ? "Stop Video" : "Start Video"}
        </button>
        <button onClick={leaveRoom} className="leave-btn">
          Leave Room
        </button>
      </div>
    </div>
  );
}
