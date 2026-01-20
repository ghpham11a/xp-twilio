import os

from fastapi import FastAPI
from contextlib import asynccontextmanager
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

from routers import chat, video

load_dotenv()

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("Starting Twilio API server...")
    yield
    print("Shutting down Twilio API server...")

def create_app() -> FastAPI:
    app = FastAPI(
        title="Twilio Chat & Video API",
        lifespan=lifespan
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["http://localhost:5173"],
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Content-Type", "Authorization"],
    )

    app.include_router(chat.router, prefix="/api/chat", tags=["Chat"])
    app.include_router(video.router, prefix="/api/video", tags=["Video"])

    @app.get("/")
    def root():
        return {"status": "up"}

    return app

# uvicorn main:app --host 0.0.0.0 --port 6969 --reload
app = create_app()