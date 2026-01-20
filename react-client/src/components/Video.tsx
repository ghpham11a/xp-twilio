import { useState, useRef, useEffect, useCallback } from "react";
import Video, {
  type Room,
  type LocalVideoTrack,
  type LocalAudioTrack,
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

  const localVideoRef = useRef<HTMLDivElement>(null);
  const remoteVideoRef = useRef<HTMLDivElement>(null);

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

    try {
      // Create room on backend first (may already exist)
      try {
        await createRoom(name);
      } catch {
        // Room might already exist, continue
      }

      // Get access token
      const { token } = await getVideoToken(identity, name);

      // Connect to room
      const videoRoom = await Video.connect(token, {
        name: name,
        audio: true,
        video: { width: 640 },
      });

      setRoom(videoRoom);
      setRoomName(name);

      // Attach local tracks
      videoRoom.localParticipant.videoTracks.forEach((publication) => {
        if (publication.track && localVideoRef.current) {
          const element = publication.track.attach();
          localVideoRef.current.appendChild(element);
        }
      });

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

  useEffect(() => {
    return () => {
      if (room) {
        room.disconnect();
      }
    };
  }, [room]);

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
