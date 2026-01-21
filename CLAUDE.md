# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Twilio Chat & Video demo application with a React frontend and FastAPI backend. It enables real-time text conversations using Twilio Conversations API and video calls using Twilio Video API.

## Development Commands

### React Client (react-client/)
```bash
cd react-client
npm install           # Install dependencies
npm run dev           # Start dev server (http://localhost:5173)
npm run build         # TypeScript compile + Vite build
npm run lint          # Run ESLint
```

### Python Server (server/)
```bash
cd server/app
pip install -r ../requirements.txt   # Install dependencies (use venv)
uvicorn main:app --host 0.0.0.0 --port 6969 --reload   # Start server
```

## Architecture

### Frontend (react-client/)
- **Vite + React 19 + TypeScript** with React Compiler enabled via Babel
- **Entry**: `src/main.tsx` → `src/App.tsx` (tab-based UI switching between Chat and Video)
- **Components**:
  - `src/components/Chat.tsx` - Text chat using `@twilio/conversations` SDK
  - `src/components/Video.tsx` - Video calls using `twilio-video` SDK
- **API Layer**: `src/api.ts` - All backend API calls (tokens, conversations, rooms)
- **Type Definitions**: `src/twilio-video.d.ts` - Custom types for twilio-video (no @types available)

### Backend (server/app/)
- **FastAPI** with router-based API organization
- **Entry**: `main.py` - App factory, CORS config (allows localhost:5173), mounts routers
- **Routers**:
  - `routers/chat.py` - `/api/chat/*` endpoints (tokens, conversations, participants, messages)
  - `routers/video.py` - `/api/video/*` endpoints (tokens, rooms, participants)

### Data Flow
1. Client requests access token from backend with identity/room info
2. Backend generates JWT using Twilio API credentials
3. Client uses token to connect directly to Twilio's real-time services
4. Conversations/rooms are managed via backend API calls to Twilio REST API

## Environment Variables (server/.env)
Required Twilio credentials:
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_API_KEY_SID`
- `TWILIO_API_KEY_SECRET`
- `TWILIO_CONVERSATIONS_SERVICE_SID` (for chat only)
