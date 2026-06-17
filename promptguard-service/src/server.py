import os
import time
import uuid
import logging

import torch
from fastapi import FastAPI
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModelForSequenceClassification

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("promptguard")

MODEL_PATH = os.environ.get("MODEL_PATH", "/models/promptguard")
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
THRESHOLD = float(os.environ.get("THRESHOLD", "0.5"))
MIN_TEXT_LENGTH = int(os.environ.get("MIN_TEXT_LENGTH", "40"))

# protectai/deberta-v3-base-prompt-injection-v2 labels: 0=SAFE, 1=INJECTION
LABEL_INJECTION = 1

app = FastAPI(title="PromptGuard Moderation Service")

tokenizer = None
model = None


@app.on_event("startup")
def load_model():
    global tokenizer, model
    logger.info("Loading PromptGuard from %s on %s", MODEL_PATH, DEVICE)
    tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL_PATH).to(DEVICE)
    model.eval()
    logger.info("PromptGuard ready (device=%s, threshold=%.2f)", DEVICE, THRESHOLD)


class ModerationRequest(BaseModel):
    model: str | None = None
    input: str | list[str]


class CategoryResult(BaseModel):
    flagged: bool
    categories: dict[str, bool]
    category_scores: dict[str, float]


class ModerationResponse(BaseModel):
    id: str
    model: str
    results: list[CategoryResult]


def classify(text: str) -> tuple[bool, float]:
    if len(text.strip()) < MIN_TEXT_LENGTH:
        return False, 0.0
    inputs = tokenizer(
        text, return_tensors="pt", truncation=True, max_length=512
    ).to(DEVICE)
    with torch.no_grad():
        logits = model(**inputs).logits
    probs = torch.softmax(logits, dim=-1)
    injection_score = probs[0][LABEL_INJECTION].item()
    return injection_score >= THRESHOLD, injection_score


@app.post("/v1/moderations", response_model=ModerationResponse)
def run_moderation(req: ModerationRequest):
    texts = [req.input] if isinstance(req.input, str) else req.input
    results = []
    for text in texts:
        start = time.time()
        flagged, score = classify(text)
        elapsed_ms = (time.time() - start) * 1000
        logger.info(
            "classify: flagged=%s injection=%.4f elapsed=%.1fms text=%.80s",
            flagged, score, elapsed_ms, repr(text),
        )
        results.append(CategoryResult(
            flagged=flagged,
            categories={"injection": flagged},
            category_scores={"injection": round(score, 4)},
        ))

    return ModerationResponse(
        id=f"modr-{uuid.uuid4().hex[:12]}",
        model=req.model or "deberta-prompt-injection-v2",
        results=results,
    )


@app.get("/health")
def health():
    return {"status": "ready" if model is not None else "loading"}
