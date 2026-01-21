import { useState, useEffect, useRef } from "react";
import { Client, Conversation, Message } from "@twilio/conversations";
import { getChatToken, joinConversationByName } from "../api";
import "./Chat.css";

export function Chat() {
  const [identity, setIdentity] = useState("");
  const [conversationName, setConversationName] = useState("");
  const [isConnected, setIsConnected] = useState(false);
  const [client, setClient] = useState<Client | null>(null);
  const [activeConversation, setActiveConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    return () => {
      if (client) {
        client.shutdown();
      }
    };
  }, [client]);

  const connectToChat = async (chatName: string) => {
    setLoading(true);
    setError(null);

    try {
      // Get token and initialize client
      const { token } = await getChatToken(identity);
      const twilioClient = new Client(token);

      // Wait for client to be connected
      await new Promise<void>((resolve, reject) => {
        const onStateChange = (state: string) => {
          if (state === "connected") {
            twilioClient.removeListener("connectionStateChanged", onStateChange);
            resolve();
          } else if (state === "disconnected") {
            twilioClient.removeListener("connectionStateChanged", onStateChange);
            reject(new Error("Connection failed"));
          }
        };
        twilioClient.on("connectionStateChanged", onStateChange);
      });

      setClient(twilioClient);

      // Join or create conversation via backend
      const convData = await joinConversationByName(chatName, identity);

      // Get conversation from Twilio client
      const conversation = await twilioClient.getConversationBySid(convData.sid);
      setActiveConversation(conversation);
      setConversationName(chatName);
      setIsConnected(true);

      // Load existing messages
      const paginator = await conversation.getMessages();
      setMessages(paginator.items);

      // Listen for new messages
      conversation.on("messageAdded", (message) => {
        setMessages((prev) => [...prev, message]);
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to join chat");
    } finally {
      setLoading(false);
    }
  };

  const handleJoinChat = async () => {
    if (!identity.trim()) {
      setError("Please enter your name");
      return;
    }
    if (!conversationName.trim()) {
      setError("Please enter a conversation name");
      return;
    }
    await connectToChat(conversationName);
  };

  const handleCreateChat = async () => {
    if (!identity.trim()) {
      setError("Please enter your name");
      return;
    }
    const uuid = crypto.randomUUID();
    await connectToChat(uuid);
  };

  const handleLeaveChat = () => {
    if (activeConversation) {
      activeConversation.removeAllListeners();
    }
    if (client) {
      client.shutdown();
    }
    setActiveConversation(null);
    setClient(null);
    setMessages([]);
    setIsConnected(false);
  };

  const handleSendMessage = async () => {
    if (!activeConversation || !newMessage.trim()) return;

    try {
      await activeConversation.sendMessage(newMessage);
      setNewMessage("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send message");
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  if (!isConnected) {
    return (
      <div className="chat-lobby">
        <h2>Chat</h2>

        <div className="join-form">
          <input
            type="text"
            placeholder="Your name"
            value={identity}
            onChange={(e) => setIdentity(e.target.value)}
            disabled={loading}
          />

          <div className="divider">
            <span>Create new chat</span>
          </div>

          <button onClick={handleCreateChat} disabled={loading} className="create-btn">
            {loading ? "Creating..." : "Create Chat"}
          </button>

          <div className="divider">
            <span>Or join existing</span>
          </div>

          <input
            type="text"
            placeholder="Conversation name"
            value={conversationName}
            onChange={(e) => setConversationName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleJoinChat()}
            disabled={loading}
          />
          <button onClick={handleJoinChat} disabled={loading}>
            {loading ? "Joining..." : "Join Chat"}
          </button>
        </div>

        {error && <p className="error">{error}</p>}
      </div>
    );
  }

  return (
    <div className="chat-room">
      <div className="chat-header">
        <div className="chat-info">
          <h3>{activeConversation?.friendlyName || conversationName}</h3>
          <span className="identity">Logged in as: {identity}</span>
        </div>
        <button onClick={handleLeaveChat} className="leave-btn">
          Leave Chat
        </button>
      </div>

      <div className="messages">
        {messages.length === 0 && (
          <div className="no-messages">
            No messages yet. Start the conversation!
          </div>
        )}
        {messages.map((msg) => (
          <div
            key={msg.sid}
            className={`message ${msg.author === identity ? "own" : ""}`}
          >
            <span className="author">{msg.author}</span>
            <span className="body">{msg.body}</span>
            <span className="time">
              {msg.dateCreated?.toLocaleTimeString()}
            </span>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div className="message-input">
        <input
          type="text"
          placeholder="Type a message..."
          value={newMessage}
          onChange={(e) => setNewMessage(e.target.value)}
          onKeyDown={handleKeyPress}
        />
        <button onClick={handleSendMessage} disabled={!newMessage.trim()}>
          Send
        </button>
      </div>

      {error && <div className="error-toast">{error}</div>}
    </div>
  );
}
