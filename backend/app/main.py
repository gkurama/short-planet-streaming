from __future__ import annotations

import asyncio
import base64
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Annotated, Literal

import jwt
import requests
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Request, UploadFile
from pydantic import BaseModel
from jwt import PyJWKClient


SUPABASE_URL = os.getenv("SUPABASE_URL", "").strip().rstrip("/")
SUPABASE_PUBLISHABLE_KEY = os.getenv("SUPABASE_PUBLISHABLE_KEY", "").strip()
FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "").strip()
MAX_UPLOAD_BYTES = max(1, int(os.getenv("MAX_UPLOAD_MB", "512"))) * 1024 * 1024
UPLOAD_CHUNK_BYTES = 1024 * 1024
YOUTUBE_API = "https://www.googleapis.com/youtube/v3"
YOUTUBE_UPLOAD_API = "https://www.googleapis.com/upload/youtube/v3/videos"
FIREBASE_JWKS = PyJWKClient(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
)

app = FastAPI(
    title="Short Planet Media API",
    version="1.0.0",
    docs_url=None,
    redoc_url=None,
)


class PublishResult(BaseModel):
    video_id: str
    watch_url: str
    privacy_status: str
    duration_seconds: float


def _bearer(value: str | None, label: str) -> str:
    if not value:
        raise HTTPException(status_code=401, detail=f"{label} ausente.")
    scheme, _, token = value.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(status_code=401, detail=f"{label} inválido.")
    return token.strip()


def _supabase_user(authorization: Annotated[str | None, Header()] = None) -> dict:
    token = _bearer(authorization, "Sessão Supabase")
    if not SUPABASE_URL or not SUPABASE_PUBLISHABLE_KEY:
        raise HTTPException(status_code=503, detail="O backend ainda não foi ligado ao projeto Supabase.")

    try:
        response = requests.get(
            f"{SUPABASE_URL}/auth/v1/user",
            headers={
                "apikey": SUPABASE_PUBLISHABLE_KEY,
                "Authorization": f"Bearer {token}",
                "Accept": "application/json",
            },
            timeout=(5, 15),
        )
    except requests.RequestException:
        raise HTTPException(status_code=503, detail="Não foi possível validar a sessão Supabase.") from None

    if response.status_code != 200:
        raise HTTPException(status_code=401, detail="A sessão Supabase expirou. Inicie sessão novamente.")
    try:
        user = response.json()
    except ValueError:
        raise HTTPException(status_code=503, detail="Resposta inválida do Supabase Auth.") from None
    if not isinstance(user, dict) or not user.get("id"):
        raise HTTPException(status_code=401, detail="A sessão Supabase não identifica um utilizador.")
    return user


def _authenticated_user(authorization: Annotated[str | None, Header()] = None) -> dict:
    token = _bearer(authorization, "Sessão do aplicativo")
    try:
        issuer = jwt.decode(token, options={"verify_signature": False}).get("iss", "")
    except jwt.PyJWTError:
        raise HTTPException(status_code=401, detail="Sessão do aplicativo inválida.") from None

    if issuer == f"{SUPABASE_URL}/auth/v1":
        return _supabase_user(f"Bearer {token}")

    if FIREBASE_PROJECT_ID and issuer == f"https://securetoken.google.com/{FIREBASE_PROJECT_ID}":
        try:
            signing_key = FIREBASE_JWKS.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                audience=FIREBASE_PROJECT_ID,
                issuer=issuer,
                options={"require": ["exp", "iat", "sub", "auth_time"]},
            )
        except jwt.PyJWTError:
            raise HTTPException(status_code=401, detail="Sessão Firebase inválida ou expirada.") from None
        return {"id": claims["sub"], "email": claims.get("email")}

    raise HTTPException(status_code=401, detail="O backend não reconhece a sessão deste aplicativo.")


def _youtube_channel_id(access_token: str) -> str:
    try:
        response = requests.get(
            f"{YOUTUBE_API}/channels",
            params={"part": "id", "mine": "true"},
            headers={"Authorization": f"Bearer {access_token}"},
            timeout=(5, 20),
        )
    except requests.RequestException:
        raise HTTPException(status_code=502, detail="Não foi possível contactar o YouTube.") from None

    if response.status_code == 401:
        raise HTTPException(status_code=401, detail="A autorização Google expirou. Ligue novamente a conta YouTube.")
    if response.status_code != 200:
        raise HTTPException(status_code=403, detail="O YouTube recusou a autorização da conta.")
    try:
        items = response.json().get("items", [])
        channel_id = items[0]["id"] if items else ""
    except (ValueError, KeyError, TypeError, IndexError):
        channel_id = ""
    if not channel_id:
        raise HTTPException(status_code=403, detail="A conta Google autorizada não tem um canal YouTube disponível.")
    return channel_id


def _run_ffmpeg(source: Path, output: Path, start_seconds: float, duration_seconds: float) -> None:
    command = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-nostdin",
        "-y",
        "-ss",
        f"{start_seconds:.3f}",
        "-i",
        str(source),
        "-t",
        f"{duration_seconds:.3f}",
        "-map",
        "0:v:0",
        "-map",
        "0:a?",
        "-vf",
        "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,fps=30",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "23",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "128k",
        "-movflags",
        "+faststart",
        str(output),
    ]
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=5400, check=False)
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=504, detail="O processamento excedeu o limite de tempo.") from None
    if completed.returncode != 0 or not output.exists() or output.stat().st_size == 0:
        raise HTTPException(status_code=422, detail="Não foi possível processar este arquivo de vídeo.")


def _probe_duration(source: Path) -> float:
    completed = None
    try:
        completed = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", str(source)],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
        duration = float(completed.stdout.strip())
    except (OSError, ValueError, subprocess.TimeoutExpired):
        duration = 0
    if completed is None or completed.returncode != 0 or duration <= 0:
        raise HTTPException(status_code=422, detail="Não foi possível ler a duração deste vídeo.")
    return duration


def _youtube_upload(
    access_token: str,
    output: Path,
    title: str,
    description: str,
    privacy_status: str,
    made_for_kids: bool,
) -> str:
    metadata = {
        "snippet": {"title": title, "description": description, "categoryId": "22"},
        "status": {
            "privacyStatus": privacy_status,
            "selfDeclaredMadeForKids": made_for_kids,
        },
    }
    size = output.stat().st_size
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json; charset=UTF-8",
        "X-Upload-Content-Type": "video/mp4",
        "X-Upload-Content-Length": str(size),
    }
    try:
        init_response = requests.post(
            YOUTUBE_UPLOAD_API,
            params={"uploadType": "resumable", "part": "snippet,status"},
            headers=headers,
            json=metadata,
            timeout=(10, 30),
        )
    except requests.RequestException:
        raise HTTPException(status_code=502, detail="Não foi possível iniciar o envio para o YouTube.") from None

    upload_url = init_response.headers.get("Location")
    if init_response.status_code not in (200, 201) or not upload_url:
        if init_response.status_code == 401:
            raise HTTPException(status_code=401, detail="A autorização de envio do YouTube expirou.")
        if init_response.status_code == 403:
            raise HTTPException(status_code=403, detail="O YouTube recusou o envio. Confirme o escopo youtube.upload e a configuração da YouTube Data API v3.")
        raise HTTPException(status_code=502, detail="O YouTube não aceitou iniciar o envio do Short.")

    try:
        with output.open("rb") as media:
            upload_response = requests.put(
                upload_url,
                headers={"Content-Type": "video/mp4", "Content-Length": str(size)},
                data=media,
                timeout=(15, 5400),
            )
    except requests.RequestException:
        raise HTTPException(status_code=502, detail="O envio para o YouTube foi interrompido. Confira o canal antes de tentar novamente.") from None

    if upload_response.status_code not in (200, 201):
        if upload_response.status_code == 401:
            raise HTTPException(status_code=401, detail="A autorização Google expirou durante o envio.")
        if upload_response.status_code == 403:
            raise HTTPException(status_code=403, detail="O YouTube recusou publicar. Confirme o escopo, a cota e o estado do projeto Google Cloud.")
        raise HTTPException(status_code=502, detail="O YouTube não concluiu o envio. Confira o canal antes de tentar novamente.")
    try:
        video_id = upload_response.json()["id"]
    except (ValueError, KeyError, TypeError):
        raise HTTPException(status_code=502, detail="O YouTube concluiu o pedido sem devolver o ID do vídeo.") from None
    return video_id


@app.get("/healthz")
def healthz() -> dict[str, str]:
    if shutil.which("ffmpeg") is None:
        raise HTTPException(status_code=503, detail="FFmpeg indisponível.")
    return {"status": "ok"}


@app.post("/v1/shorts/publish", response_model=PublishResult)
async def publish_short(
    _user: Annotated[dict, Depends(_authenticated_user)],
    file: Annotated[UploadFile, File(description="Vídeo original escolhido no aparelho e autorizado para edição")],
    start_seconds: Annotated[float, Form(ge=0)],
    end_seconds: Annotated[float, Form(gt=0)],
    title: Annotated[str, Form(min_length=1, max_length=100)],
    description: Annotated[str, Form(max_length=5000)] = "",
    privacy_status: Annotated[Literal["private", "unlisted", "public"], Form()] = "unlisted",
    made_for_kids: Annotated[bool, Form()] = False,
    youtube_authorization: Annotated[str | None, Header(alias="X-YouTube-Authorization")] = None,
) -> PublishResult:
    access_token = _bearer(youtube_authorization, "Autorização YouTube")
    clean_title = title.strip()
    if not clean_title:
        raise HTTPException(status_code=422, detail="O título é obrigatório.")
    duration_seconds = end_seconds - start_seconds
    if duration_seconds <= 0 or duration_seconds > 180:
        raise HTTPException(status_code=422, detail="O trecho precisa ter entre 1 segundo e 3 minutos.")
    if file.content_type and not file.content_type.lower().startswith("video/"):
        raise HTTPException(status_code=415, detail="Escolha um arquivo de vídeo.")

    with tempfile.TemporaryDirectory(prefix="shortplanet-") as temporary_directory:
        workdir = Path(temporary_directory)
        suffix = Path(file.filename or "source.mp4").suffix.lower()
        if suffix not in {".mp4", ".mov", ".m4v", ".mkv", ".webm", ".avi"}:
            suffix = ".video"
        source = workdir / f"source{suffix}"
        output = workdir / "short.mp4"

        total = 0
        try:
            with source.open("wb") as destination:
                while chunk := await file.read(UPLOAD_CHUNK_BYTES):
                    total += len(chunk)
                    if total > MAX_UPLOAD_BYTES:
                        raise HTTPException(status_code=413, detail=f"O vídeo excede o limite de {MAX_UPLOAD_BYTES // (1024 * 1024)} MB.")
                    destination.write(chunk)
        finally:
            await file.close()
        if total == 0:
            raise HTTPException(status_code=400, detail="O arquivo de vídeo está vazio.")

        source_duration = await asyncio.to_thread(_probe_duration, source)
        if start_seconds >= source_duration:
            raise HTTPException(status_code=422, detail="O início selecionado ultrapassa a duração do vídeo.")
        actual_duration = min(end_seconds, source_duration) - start_seconds
        if actual_duration <= 0 or actual_duration > 180:
            raise HTTPException(status_code=422, detail="O trecho precisa ter entre 1 segundo e 3 minutos.")

        await asyncio.to_thread(_youtube_channel_id, access_token)
        await asyncio.to_thread(_run_ffmpeg, source, output, start_seconds, actual_duration)
        video_id = await asyncio.to_thread(
            _youtube_upload,
            access_token,
            output,
            clean_title,
            description.strip(),
            privacy_status,
            made_for_kids,
        )
        # The token and media are held only for this request and temporary directory.

    return PublishResult(
        video_id=video_id,
        watch_url=f"https://www.youtube.com/shorts/{video_id}",
        privacy_status=privacy_status,
        duration_seconds=round(actual_duration, 2),
    )


@app.post("/v1/shorts/publish-stream", response_model=PublishResult)
async def publish_short_stream(
    request: Request,
    _user: Annotated[dict, Depends(_authenticated_user)],
    youtube_authorization: Annotated[str | None, Header(alias="X-YouTube-Authorization")] = None,
) -> PublishResult:
    """Accept the selected original as the request body, avoiding a second full copy on the phone."""
    access_token = _bearer(youtube_authorization, "Autorização YouTube")
    encoded_metadata = request.headers.get("x-short-metadata", "")
    try:
        padded_metadata = encoded_metadata + "=" * (-len(encoded_metadata) % 4)
        metadata = json.loads(base64.b64decode(padded_metadata, validate=True))
    except (ValueError, json.JSONDecodeError):
        raise HTTPException(status_code=400, detail="Metadados do corte inválidos.") from None
    if not isinstance(metadata, dict):
        raise HTTPException(status_code=400, detail="Metadados do corte inválidos.")

    try:
        start_seconds = float(metadata["start_seconds"])
        end_seconds = float(metadata["end_seconds"])
    except (KeyError, TypeError, ValueError):
        raise HTTPException(status_code=422, detail="O intervalo de corte é inválido.") from None
    title = metadata.get("title")
    description = metadata.get("description", "")
    privacy_status = metadata.get("privacy_status", "unlisted")
    made_for_kids = metadata.get("made_for_kids", False)
    if not isinstance(title, str) or not title.strip() or len(title) > 100:
        raise HTTPException(status_code=422, detail="O título é obrigatório e pode ter até 100 caracteres.")
    if not isinstance(description, str) or len(description) > 5000:
        raise HTTPException(status_code=422, detail="A descrição excede 5000 caracteres.")
    if privacy_status not in {"private", "unlisted", "public"}:
        raise HTTPException(status_code=422, detail="Escolhe uma visibilidade válida para o YouTube.")
    if not isinstance(made_for_kids, bool):
        raise HTTPException(status_code=422, detail="A declaração de conteúdo infantil é inválida.")
    requested_duration = end_seconds - start_seconds
    if start_seconds < 0 or requested_duration <= 0 or requested_duration > 180:
        raise HTTPException(status_code=422, detail="O trecho precisa ter entre 1 segundo e 3 minutos.")

    content_type = request.headers.get("content-type", "").lower()
    if not content_type.startswith("video/"):
        raise HTTPException(status_code=415, detail="O corpo do pedido precisa ser um arquivo de vídeo.")
    try:
        content_length = int(request.headers.get("content-length", "0"))
    except ValueError:
        content_length = 0
    if content_length > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail=f"O vídeo excede o limite de {MAX_UPLOAD_BYTES // (1024 * 1024)} MB.")

    extension = ".mov" if content_type == "video/quicktime" else (".m4v" if content_type == "video/x-m4v" else ".mp4")
    with tempfile.TemporaryDirectory(prefix="shortplanet-") as temporary_directory:
        workdir = Path(temporary_directory)
        source = workdir / f"source{extension}"
        output = workdir / "short.mp4"
        total = 0
        try:
            with source.open("wb") as destination:
                async for chunk in request.stream():
                    if not chunk:
                        continue
                    total += len(chunk)
                    if total > MAX_UPLOAD_BYTES:
                        raise HTTPException(
                            status_code=413,
                            detail=f"O vídeo excede o limite de {MAX_UPLOAD_BYTES // (1024 * 1024)} MB.",
                        )
                    destination.write(chunk)
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(status_code=400, detail="A transferência do vídeo foi interrompida.") from None

        if total == 0:
            raise HTTPException(status_code=400, detail="O arquivo de vídeo está vazio.")
        source_duration = await asyncio.to_thread(_probe_duration, source)
        if start_seconds >= source_duration:
            raise HTTPException(status_code=422, detail="O início selecionado ultrapassa a duração do vídeo.")
        actual_duration = min(end_seconds, source_duration) - start_seconds
        if actual_duration <= 0 or actual_duration > 180:
            raise HTTPException(status_code=422, detail="O trecho precisa ter entre 1 segundo e 3 minutos.")

        await asyncio.to_thread(_youtube_channel_id, access_token)
        await asyncio.to_thread(_run_ffmpeg, source, output, start_seconds, actual_duration)
        video_id = await asyncio.to_thread(
            _youtube_upload,
            access_token,
            output,
            title.strip(),
            description.strip(),
            privacy_status,
            made_for_kids,
        )

    return PublishResult(
        video_id=video_id,
        watch_url=f"https://www.youtube.com/shorts/{video_id}",
        privacy_status=privacy_status,
        duration_seconds=round(actual_duration, 2),
    )
