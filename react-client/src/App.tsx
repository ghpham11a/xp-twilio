import { useState } from "react";
import { Chat } from "./components/Chat";
import { VideoChat } from "./components/Video";
import "./App.css";

type Tab = "chat" | "video";

function App() {
  const [activeTab, setActiveTab] = useState<Tab>("chat");

  return (
    <div className="app">
      <header className="app-header">
        <h1>Twilio Chat & Video</h1>
        <nav className="tabs">
          <button
            className={`tab ${activeTab === "chat" ? "active" : ""}`}
            onClick={() => setActiveTab("chat")}
          >
            Chat
          </button>
          <button
            className={`tab ${activeTab === "video" ? "active" : ""}`}
            onClick={() => setActiveTab("video")}
          >
            Video
          </button>
        </nav>
      </header>

      <main className="app-content">
        {activeTab === "chat" ? <Chat /> : <VideoChat />}
      </main>
    </div>
  );
}

export default App;
