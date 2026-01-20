const API_BASE_URL = "http://localhost:6969/api";

export interface ChatTokenResponse {
  token: string;
  identity: string;
}

export interface VideoTokenResponse {
  token: string;
  identity: string;
  room_name: string;
}

export interface Conversation {
  sid: string;
  friendly_name: string | null;
  date_created: string | null;
  state: string;
}

export interface Room {
  sid: string;
  unique_name: string;
  status: string;
  type: string;
  date_created: string | null;
  duration: number | null;
}

// Chat API
export async function getChatToken(identity: string): Promise<ChatTokenResponse> {
  const response = await fetch(`${API_BASE_URL}/chat/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ identity }),
  });
  if (!response.ok) throw new Error("Failed to get chat token");
  return response.json();
}

export async function createConversation(friendlyName?: string): Promise<Conversation> {
  const response = await fetch(`${API_BASE_URL}/chat/conversations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ friendly_name: friendlyName }),
  });
  if (!response.ok) throw new Error("Failed to create conversation");
  return response.json();
}

export async function listConversations(): Promise<{ conversations: Conversation[] }> {
  const response = await fetch(`${API_BASE_URL}/chat/conversations`);
  if (!response.ok) throw new Error("Failed to list conversations");
  return response.json();
}

export async function joinConversation(conversationSid: string, identity: string) {
  const response = await fetch(`${API_BASE_URL}/chat/conversations/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ conversation_sid: conversationSid, identity }),
  });
  if (!response.ok) throw new Error("Failed to join conversation");
  return response.json();
}

export async function joinConversationByName(conversationName: string, identity: string): Promise<Conversation> {
  const response = await fetch(`${API_BASE_URL}/chat/conversations/join-by-name`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ conversation_name: conversationName, identity }),
  });
  if (!response.ok) throw new Error("Failed to join conversation");
  return response.json();
}

export async function deleteConversation(conversationSid: string) {
  const response = await fetch(`${API_BASE_URL}/chat/conversations/${conversationSid}`, {
    method: "DELETE",
  });
  if (!response.ok) throw new Error("Failed to delete conversation");
  return response.json();
}

// Video API
export async function getVideoToken(identity: string, roomName: string): Promise<VideoTokenResponse> {
  const response = await fetch(`${API_BASE_URL}/video/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ identity, room_name: roomName }),
  });
  if (!response.ok) throw new Error("Failed to get video token");
  return response.json();
}

export async function createRoom(roomName: string, roomType: string = "group"): Promise<Room> {
  const response = await fetch(`${API_BASE_URL}/video/rooms`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ room_name: roomName, room_type: roomType }),
  });
  if (!response.ok) throw new Error("Failed to create room");
  return response.json();
}

export async function listRooms(status: string = "in-progress"): Promise<{ rooms: Room[] }> {
  const response = await fetch(`${API_BASE_URL}/video/rooms?status=${status}`);
  if (!response.ok) throw new Error("Failed to list rooms");
  return response.json();
}

export async function endRoom(roomSid: string) {
  const response = await fetch(`${API_BASE_URL}/video/rooms/${roomSid}/end`, {
    method: "POST",
  });
  if (!response.ok) throw new Error("Failed to end room");
  return response.json();
}
