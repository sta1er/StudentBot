from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import httpx
import logging
import json
from typing import Optional, Dict, Any
import os
from datetime import datetime

# Настройка логирования
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Student Bot AI Service", version="1.0.0")

# Конфигурация
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
#DEFAULT_MODEL = "llama2:7b-chat"
DEFAULT_MODEL = "phi3:mini"
CODE_MODEL = "codellama:7b"
MAX_TOKENS = int(os.getenv("MAX_TOKENS", "2000"))
TEMPERATURE = float(os.getenv("TEMPERATURE", "0.7"))

class ChatRequest(BaseModel):
    message: str
    context: Optional[str] = ""
    max_tokens: Optional[int] = MAX_TOKENS
    temperature: Optional[float] = TEMPERATURE
    task_type: Optional[str] = "general"
    book_id: Optional[str] = None
    timestamp: Optional[int] = None

class ChatResponse(BaseModel):
    response: str
    model_used: str
    processing_time_ms: int
    timestamp: int

class HealthResponse(BaseModel):
    status: str
    ollama_available: bool
    available_models: list
    timestamp: int

@app.get("/", response_model=Dict[str, str])
async def root():
    """Корневой endpoint"""
    return {
        "service": "Student Bot AI Service",
        "version": "1.0.0",
        "status": "running"
    }

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Проверка состояния сервиса"""
    start_time = datetime.now()
    
    try:
        # Проверяем доступность Ollama
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{OLLAMA_BASE_URL}/api/version", timeout=5.0)
            ollama_available = response.status_code == 200
            
            # Получаем список доступных моделей
            if ollama_available:
                models_response = await client.get(f"{OLLAMA_BASE_URL}/api/tags", timeout=10.0)
                if models_response.status_code == 200:
                    models_data = models_response.json()
                    available_models = [model["name"] for model in models_data.get("models", [])]
                else:
                    available_models = []
            else:
                available_models = []
    
    except Exception as e:
        logger.error(f"Ошибка при проверке Ollama: {e}")
        ollama_available = False
        available_models = []
    
    return HealthResponse(
        status="healthy" if ollama_available else "degraded",
        ollama_available=ollama_available,
        available_models=available_models,
        timestamp=int(datetime.now().timestamp())
    )

@app.post("/api/chat", response_model=ChatResponse)
async def chat_completion(request: ChatRequest):
    """Основной endpoint для обработки запросов к ИИ"""
    start_time = datetime.now()
    
    try:
        # Выбираем модель в зависимости от типа задачи
        model = select_model(request.task_type)
        
        # Формируем промпт
        prompt = build_prompt(request)
        
        # Отправляем запрос к Ollama
        ai_response = await query_ollama(model, prompt, request.temperature, request.max_tokens)
        
        processing_time = int((datetime.now() - start_time).total_seconds() * 1000)
        
        logger.info(f"Обработан запрос за {processing_time}мс, модель: {model}")
        
        return ChatResponse(
            response=ai_response,
            model_used=model,
            processing_time_ms=processing_time,
            timestamp=int(datetime.now().timestamp())
        )
        
    except Exception as e:
        logger.error(f"Ошибка при обработке запроса: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка обработки: {str(e)}")

@app.post("/api/summarize")
async def summarize_text(request: ChatRequest):
    """Специализированный endpoint для создания краткого содержания"""
    try:
        model = DEFAULT_MODEL
        
        summarize_prompt = f"""
Создай структурированное краткое содержание следующего текста.
Выдели:
1. Основные темы
2. Ключевые идеи
3. Важные выводы
4. Практические применения (если есть)

Текст для анализа:
{request.context}

Вопрос/Фокус: {request.message}

Ответ должен быть структурированным и легким для понимания студентами.
"""
        
        response = await query_ollama(model, summarize_prompt, 0.3, request.max_tokens)
        
        return {
            "response": response,
            "model_used": model,
            "task_type": "summarization"
        }
        
    except Exception as e:
        logger.error(f"Ошибка при создании краткого содержания: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/explain")
async def explain_concept(request: ChatRequest):
    """Специализированный endpoint для объяснения концепций"""
    try:
        model = DEFAULT_MODEL
        
        explain_prompt = f"""
Объясни следующую концепцию простыми словами, подходящими для студентов:

Концепция: {request.message}

Контекст: {request.context}

Пожалуйста:
1. Дай простое определение
2. Приведи 2-3 примера
3. Объясни, почему это важно
4. Предложи способы запомнить или понять лучше

Избегай сложных терминов без объяснений.
"""
        
        response = await query_ollama(model, explain_prompt, 0.5, request.max_tokens)
        
        return {
            "response": response,
            "model_used": model,
            "task_type": "explanation"
        }
        
    except Exception as e:
        logger.error(f"Ошибка при объяснении концепции: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/homework-help")
async def homework_help(request: ChatRequest):
    """Специализированный endpoint для помощи с домашними заданиями"""
    try:
        model = select_model("homework")
        
        homework_prompt = f"""
Помоги студенту с домашним заданием, но НЕ давай готовые ответы.
Вместо этого:
1. Направляй к правильному подходу
2. Задавай наводящие вопросы
3. Объясняй методы решения
4. Подсказывай, где искать информацию

Домашнее задание: {request.message}

Доступные материалы: {request.context}

Помни: цель - научить студента думать самостоятельно!
"""
        
        response = await query_ollama(model, homework_prompt, 0.4, request.max_tokens)
        
        return {
            "response": response,
            "model_used": model,
            "task_type": "homework_help"
        }
        
    except Exception as e:
        logger.error(f"Ошибка при помощи с домашним заданием: {e}")
        raise HTTPException(status_code=500, detail=str(e))

def select_model(task_type: str) -> str:
    """Выбор модели в зависимости от типа задачи"""
    if task_type in ["code", "programming", "coding"]:
        return CODE_MODEL
    elif task_type in ["homework", "homework_help", "explanation"]:
        return DEFAULT_MODEL
    else:
        return DEFAULT_MODEL

def build_prompt(request: ChatRequest) -> str:
    """Построение промпта для ИИ"""
    base_prompt = """Ты - помощник для студентов. Отвечай на русском языке, будь дружелюбным и полезным.
Если не знаешь ответа, честно скажи об этом и предложи альтернативы.

"""
    
    if request.context:
        base_prompt += f"Контекст:\n{request.context}\n\n"
    
    if request.book_id:
        base_prompt += f"Этот вопрос относится к книге ID: {request.book_id}\n\n"
    
    base_prompt += f"Вопрос студента: {request.message}\n\nОтвет:"
    
    return base_prompt

async def query_ollama(model: str, prompt: str, temperature: float, max_tokens: int) -> str:
    """Отправка запроса к Ollama"""
    try:
        async with httpx.AsyncClient(timeout=600.0) as client:
            payload = {
                "model": model,
                "prompt": prompt,
                "stream": False,
                "options": {
                    "temperature": temperature,
                    "num_predict": max_tokens,
                    "top_p": 0.9,
                    "top_k": 40
                }
            }
            
            logger.info(f"Отправка запроса к модели {model}")
            
            response = await client.post(
                f"{OLLAMA_BASE_URL}/api/generate",
                json=payload
            )
            
            if response.status_code != 200:
                raise HTTPException(
                    status_code=response.status_code,
                    detail=f"Ошибка Ollama: {response.text}"
                )
            
            result = response.json()
            return result.get("response", "Извините, не удалось получить ответ.")
            
    except httpx.TimeoutException:
        logger.error("Таймаут при запросе к Ollama")
        raise HTTPException(status_code=504, detail="Таймаут при обработке запроса")
    except Exception as e:
        logger.error(f"Ошибка при запросе к Ollama: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка ИИ сервиса: {str(e)}")

@app.get("/api/models")
async def get_available_models():
    """Получить список доступных моделей"""
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{OLLAMA_BASE_URL}/api/tags", timeout=10.0)
            if response.status_code == 200:
                return response.json()
            else:
                raise HTTPException(status_code=500, detail="Не удалось получить список моделей")
    except Exception as e:
        logger.error(f"Ошибка при получении списка моделей: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)