declare module "twilio-video" {
  export interface ConnectOptions {
    name?: string;
    audio?: boolean | MediaTrackConstraints;
    video?: boolean | MediaTrackConstraints;
    dominantSpeaker?: boolean;
    networkQuality?: boolean | { local?: number; remote?: number };
    bandwidthProfile?: object;
    maxAudioBitrate?: number;
    maxVideoBitrate?: number;
    preferredAudioCodecs?: string[];
    preferredVideoCodecs?: string[] | { codec: string; simulcast?: boolean }[];
    logLevel?: "debug" | "info" | "warn" | "error" | "off";
    tracks?: (LocalAudioTrack | LocalVideoTrack)[];
    region?: string;
  }

  export interface Room {
    sid: string;
    name: string;
    state: "connected" | "disconnected" | "reconnecting";
    localParticipant: LocalParticipant;
    participants: Map<string, RemoteParticipant>;
    dominantSpeaker: RemoteParticipant | null;
    disconnect(): void;
    on(event: "participantConnected", listener: (participant: RemoteParticipant) => void): this;
    on(event: "participantDisconnected", listener: (participant: RemoteParticipant) => void): this;
    on(event: "disconnected", listener: (room: Room, error?: Error) => void): this;
    on(event: "trackSubscribed", listener: (track: RemoteTrack, publication: RemoteTrackPublication, participant: RemoteParticipant) => void): this;
    on(event: "trackUnsubscribed", listener: (track: RemoteTrack, publication: RemoteTrackPublication, participant: RemoteParticipant) => void): this;
    on(event: string, listener: (...args: unknown[]) => void): this;
  }

  export interface Participant {
    sid: string;
    identity: string;
    state: "connected" | "disconnected" | "reconnecting";
    tracks: Map<string, TrackPublication>;
    audioTracks: Map<string, AudioTrackPublication>;
    videoTracks: Map<string, VideoTrackPublication>;
    on(event: string, listener: (...args: unknown[]) => void): this;
  }

  export interface LocalParticipant extends Participant {
    audioTracks: Map<string, LocalAudioTrackPublication>;
    videoTracks: Map<string, LocalVideoTrackPublication>;
    publishTrack(track: LocalAudioTrack | LocalVideoTrack): Promise<LocalTrackPublication>;
    unpublishTrack(track: LocalAudioTrack | LocalVideoTrack): LocalTrackPublication;
  }

  export interface RemoteParticipant extends Omit<Participant, "tracks" | "audioTracks" | "videoTracks"> {
    tracks: Map<string, RemoteTrackPublication>;
    audioTracks: Map<string, RemoteAudioTrackPublication>;
    videoTracks: Map<string, RemoteVideoTrackPublication>;
    on(event: "trackSubscribed", listener: (track: RemoteTrack) => void): this;
    on(event: "trackUnsubscribed", listener: (track: RemoteTrack) => void): this;
    on(event: "trackPublished", listener: (publication: RemoteTrackPublication) => void): this;
    on(event: "trackUnpublished", listener: (publication: RemoteTrackPublication) => void): this;
  }

  export interface Track {
    kind: "audio" | "video" | "data";
    name: string;
    sid?: string;
  }

  export interface AudioTrack extends Track {
    kind: "audio";
    isEnabled: boolean;
    attach(element?: HTMLMediaElement): HTMLMediaElement;
    detach(): HTMLMediaElement[];
    detach(element: HTMLMediaElement): HTMLMediaElement;
  }

  export interface VideoTrack extends Track {
    kind: "video";
    isEnabled: boolean;
    dimensions: { width: number | null; height: number | null };
    attach(element?: HTMLMediaElement): HTMLMediaElement;
    detach(): HTMLMediaElement[];
    detach(element: HTMLMediaElement): HTMLMediaElement;
  }

  export interface LocalAudioTrack extends AudioTrack {
    enable(): this;
    disable(): this;
    stop(): void;
  }

  export interface LocalVideoTrack extends VideoTrack {
    enable(): this;
    disable(): this;
    stop(): void;
  }

  export interface RemoteAudioTrack extends AudioTrack {}
  export interface RemoteVideoTrack extends VideoTrack {}

  export type LocalTrack = LocalAudioTrack | LocalVideoTrack;
  export type RemoteTrack = RemoteAudioTrack | RemoteVideoTrack;

  export interface TrackPublication {
    trackSid: string;
    trackName: string;
    kind: "audio" | "video" | "data";
    track: Track | null;
    isTrackEnabled: boolean;
  }

  export interface AudioTrackPublication extends TrackPublication {
    kind: "audio";
    track: AudioTrack | null;
  }

  export interface VideoTrackPublication extends TrackPublication {
    kind: "video";
    track: VideoTrack | null;
  }

  export interface LocalTrackPublication extends TrackPublication {
    track: LocalTrack | null;
  }

  export interface LocalAudioTrackPublication extends LocalTrackPublication, AudioTrackPublication {
    track: LocalAudioTrack | null;
  }

  export interface LocalVideoTrackPublication extends LocalTrackPublication, VideoTrackPublication {
    track: LocalVideoTrack | null;
  }

  export interface RemoteTrackPublication extends TrackPublication {
    isSubscribed: boolean;
    track: RemoteTrack | null;
  }

  export interface RemoteAudioTrackPublication extends RemoteTrackPublication, AudioTrackPublication {
    track: RemoteAudioTrack | null;
  }

  export interface RemoteVideoTrackPublication extends RemoteTrackPublication, VideoTrackPublication {
    track: RemoteVideoTrack | null;
  }

  export function connect(token: string, options?: ConnectOptions): Promise<Room>;
  export function createLocalAudioTrack(options?: MediaTrackConstraints): Promise<LocalAudioTrack>;
  export function createLocalVideoTrack(options?: MediaTrackConstraints): Promise<LocalVideoTrack>;
  export function createLocalTracks(options?: { audio?: boolean | MediaTrackConstraints; video?: boolean | MediaTrackConstraints }): Promise<LocalTrack[]>;

  const Video: {
    connect: typeof connect;
    createLocalAudioTrack: typeof createLocalAudioTrack;
    createLocalVideoTrack: typeof createLocalVideoTrack;
    createLocalTracks: typeof createLocalTracks;
  };

  export default Video;
}
