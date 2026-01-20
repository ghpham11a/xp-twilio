import os
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from twilio.rest import Client
from twilio.jwt.access_token import AccessToken
from twilio.jwt.access_token.grants import VideoGrant

router = APIRouter()

def get_twilio_client() -> Client:
    ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID")
    AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN")
    if not ACCOUNT_SID or not AUTH_TOKEN:
        raise HTTPException(status_code=500, detail="Twilio credentials not configured")
    return Client(ACCOUNT_SID, AUTH_TOKEN)


class TokenRequest(BaseModel):
    identity: str
    room_name: str


class RoomRequest(BaseModel):
    room_name: str
    room_type: str = "group"  # "peer-to-peer", "group", or "group-small"


@router.post("/token")
def get_video_token(request: TokenRequest):

    # Twilio credentials from environment
    ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID")
    API_KEY_SID = os.getenv("TWILIO_API_KEY_SID")
    API_KEY_SECRET = os.getenv("TWILIO_API_KEY_SECRET")

    """Generate an access token for Twilio Video"""
    if not all([ACCOUNT_SID, API_KEY_SID, API_KEY_SECRET]):
        raise HTTPException(status_code=500, detail="Twilio credentials not fully configured")

    token = AccessToken(
        ACCOUNT_SID,
        API_KEY_SID,
        API_KEY_SECRET,
        identity=request.identity,
        ttl=3600
    )

    video_grant = VideoGrant(room=request.room_name)
    token.add_grant(video_grant)

    return {
        "token": token.to_jwt(),
        "identity": request.identity,
        "room_name": request.room_name
    }


@router.post("/rooms")
def create_room(request: RoomRequest):
    """Create a new video room"""
    client = get_twilio_client()

    try:
        room = client.video.v1.rooms.create(
            unique_name=request.room_name,
            type=request.room_type
        )

        return {
            "sid": room.sid,
            "unique_name": room.unique_name,
            "status": room.status,
            "type": room.type,
            "date_created": room.date_created.isoformat() if room.date_created else None
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/rooms")
def list_rooms(status: str = "in-progress"):
    """List video rooms"""
    client = get_twilio_client()

    try:
        rooms = client.video.v1.rooms.list(status=status, limit=50)

        return {
            "rooms": [
                {
                    "sid": room.sid,
                    "unique_name": room.unique_name,
                    "status": room.status,
                    "type": room.type,
                    "date_created": room.date_created.isoformat() if room.date_created else None,
                    "duration": room.duration
                }
                for room in rooms
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/rooms/{room_sid}")
def get_room(room_sid: str):
    """Get a specific video room"""
    client = get_twilio_client()

    try:
        room = client.video.v1.rooms(room_sid).fetch()

        return {
            "sid": room.sid,
            "unique_name": room.unique_name,
            "status": room.status,
            "type": room.type,
            "date_created": room.date_created.isoformat() if room.date_created else None,
            "duration": room.duration
        }
    except Exception as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get("/rooms/{room_sid}/participants")
def list_room_participants(room_sid: str):
    """List participants in a video room"""
    client = get_twilio_client()

    try:
        participants = client.video.v1.rooms(room_sid).participants.list()

        return {
            "participants": [
                {
                    "sid": p.sid,
                    "identity": p.identity,
                    "status": p.status,
                    "date_created": p.date_created.isoformat() if p.date_created else None
                }
                for p in participants
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/rooms/{room_sid}/end")
def end_room(room_sid: str):
    """End a video room"""
    client = get_twilio_client()

    try:
        room = client.video.v1.rooms(room_sid).update(status="completed")

        return {
            "sid": room.sid,
            "unique_name": room.unique_name,
            "status": room.status,
            "message": "Room ended successfully"
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
