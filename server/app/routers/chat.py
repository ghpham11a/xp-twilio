import os
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from twilio.rest import Client
from twilio.jwt.access_token import AccessToken
from twilio.jwt.access_token.grants import ChatGrant

router = APIRouter()

def get_twilio_client() -> Client:
    ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID")
    AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN")
    if not ACCOUNT_SID or not AUTH_TOKEN:
        raise HTTPException(status_code=500, detail="Twilio credentials not configured")
    return Client(ACCOUNT_SID, AUTH_TOKEN)


class TokenRequest(BaseModel):
    identity: str


class ConversationRequest(BaseModel):
    friendly_name: str | None = None


class JoinConversationRequest(BaseModel):
    conversation_sid: str
    identity: str


class JoinByNameRequest(BaseModel):
    conversation_name: str
    identity: str


class SendMessageRequest(BaseModel):
    conversation_sid: str
    author: str
    body: str


@router.post("/token")
def get_chat_token(request: TokenRequest):

    # Twilio credentials from environment
    ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID")
    API_KEY_SID = os.getenv("TWILIO_API_KEY_SID")
    API_KEY_SECRET = os.getenv("TWILIO_API_KEY_SECRET")
    CONVERSATIONS_SERVICE_SID = os.getenv("TWILIO_CONVERSATIONS_SERVICE_SID")

    """Generate an access token for Twilio Conversations"""
    if not all([ACCOUNT_SID, API_KEY_SID, API_KEY_SECRET, CONVERSATIONS_SERVICE_SID]):
        raise HTTPException(status_code=500, detail="Twilio credentials not fully configured")
    
    try:
        token = AccessToken(
            ACCOUNT_SID,
            API_KEY_SID,
            API_KEY_SECRET,
            identity=request.identity,
            ttl=3600
        )

        chat_grant = ChatGrant(service_sid=CONVERSATIONS_SERVICE_SID)
        token.add_grant(chat_grant)

        return {
            "token": token.to_jwt(),
            "identity": request.identity
        }
    except Exception as e:
        print(e)
        raise HTTPException(status_code=500, detail=str(e)) 


@router.post("/conversations")
def create_conversation(request: ConversationRequest):
    """Create a new conversation"""
    client = get_twilio_client()

    try:
        conversation = client.conversations.v1.conversations.create(
            friendly_name=request.friendly_name
        )
        return {
            "sid": conversation.sid,
            "friendly_name": conversation.friendly_name,
            "date_created": conversation.date_created.isoformat() if conversation.date_created else None,
            "state": conversation.state
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/conversations")
def list_conversations():
    """List all conversations"""
    client = get_twilio_client()

    try:
        conversations = client.conversations.v1.conversations.list(limit=50)
        return {
            "conversations": [
                {
                    "sid": conv.sid,
                    "friendly_name": conv.friendly_name,
                    "date_created": conv.date_created.isoformat() if conv.date_created else None,
                    "state": conv.state
                }
                for conv in conversations
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/conversations/{conversation_sid}")
def get_conversation(conversation_sid: str):
    """Get a specific conversation"""
    client = get_twilio_client()

    try:
        conversation = client.conversations.v1.conversations(conversation_sid).fetch()
        return {
            "sid": conversation.sid,
            "friendly_name": conversation.friendly_name,
            "date_created": conversation.date_created.isoformat() if conversation.date_created else None,
            "state": conversation.state
        }
    except Exception as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/conversations/join")
def join_conversation(request: JoinConversationRequest):
    """Add a participant to a conversation"""
    client = get_twilio_client()

    try:
        participant = client.conversations.v1.conversations(
            request.conversation_sid
        ).participants.create(identity=request.identity)

        return {
            "sid": participant.sid,
            "conversation_sid": participant.conversation_sid,
            "identity": participant.identity
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/conversations/join-by-name")
def join_or_create_conversation(request: JoinByNameRequest):
    """Find or create a conversation by name, then add participant"""
    client = get_twilio_client()

    try:
        # Try to find existing conversation by friendly_name
        conversations = client.conversations.v1.conversations.list(limit=100)
        conversation = None

        for conv in conversations:
            if conv.friendly_name == request.conversation_name:
                conversation = conv
                break

        # Create if not found
        if not conversation:
            conversation = client.conversations.v1.conversations.create(
                friendly_name=request.conversation_name
            )

        # Try to add participant (may already exist)
        try:
            client.conversations.v1.conversations(
                conversation.sid
            ).participants.create(identity=request.identity)
        except Exception:
            # Participant might already exist, that's ok
            pass

        return {
            "sid": conversation.sid,
            "friendly_name": conversation.friendly_name,
            "date_created": conversation.date_created.isoformat() if conversation.date_created else None,
            "state": conversation.state
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/conversations/{conversation_sid}/participants")
def list_participants(conversation_sid: str):
    """List participants in a conversation"""
    client = get_twilio_client()

    try:
        participants = client.conversations.v1.conversations(
            conversation_sid
        ).participants.list()

        return {
            "participants": [
                {
                    "sid": p.sid,
                    "identity": p.identity,
                    "date_created": p.date_created.isoformat() if p.date_created else None
                }
                for p in participants
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/messages")
def send_message(request: SendMessageRequest):
    """Send a message to a conversation"""
    client = get_twilio_client()

    try:
        message = client.conversations.v1.conversations(
            request.conversation_sid
        ).messages.create(
            author=request.author,
            body=request.body
        )

        return {
            "sid": message.sid,
            "conversation_sid": message.conversation_sid,
            "author": message.author,
            "body": message.body,
            "date_created": message.date_created.isoformat() if message.date_created else None
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/conversations/{conversation_sid}/messages")
def get_messages(conversation_sid: str):
    """Get messages from a conversation"""
    client = get_twilio_client()

    try:
        messages = client.conversations.v1.conversations(
            conversation_sid
        ).messages.list(limit=100)

        return {
            "messages": [
                {
                    "sid": msg.sid,
                    "author": msg.author,
                    "body": msg.body,
                    "date_created": msg.date_created.isoformat() if msg.date_created else None
                }
                for msg in messages
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.delete("/conversations/{conversation_sid}")
def delete_conversation(conversation_sid: str):
    """Delete a conversation"""
    client = get_twilio_client()

    try:
        client.conversations.v1.conversations(conversation_sid).delete()
        return {"success": True, "message": "Conversation deleted"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
