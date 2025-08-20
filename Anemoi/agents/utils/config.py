"""Configuration file for model settings"""
import os

from camel.models import ModelFactory, BaseModelBackend
from camel.types import ModelType, ModelPlatformType

PLATFORM_TYPE = "openrouter"

#openrouter url
URL = "https://openrouter.ai/api/v1"

MODEL_TYPE = "openai/gpt-4.1-mini"
MODEL_TYPE_WORKER = "openai/gpt-4o"
MODEL_TYPE_WEB = "openai/gpt-4o"
MODEL_TYPE_WEB_PLANNING = "openai/o3-mini"
MODEL_TYPE_IMAGE = "openai/gpt-4o"
MODEL_TYPE_AUDIO = "openai/o3-mini"

# Model Settings
MODEL_CONFIG = {
    "temperature": 0,
    "frequency_penalty": 0,
    "top_p": 0.99,
    #"max_tokens": 128000,
}


WORKER_MODEL_CONFIG = {
    "temperature": 0,
    "frequency_penalty": 0,
    "top_p": 0.99,
    # "max_tokens": 1600,
}

WEB_MODEL_CONFIG = {
    "temperature": 0,
    "frequency_penalty": 0,
    "top_p": 0.99,
    #"max_tokens": 127000,
}

WEB_PLANNING_MODEL_CONFIG = {
    #"max_tokens": 200000,
}

IMAGE_MODEL_CONFIG = {
    "temperature": 0,
    "frequency_penalty": 0,
    "top_p": 0.99,
    #"max_tokens": 60000,
}

AUDIO_MODEL_CONFIG = {
    #"max_tokens": 60000,
}

# Agent Settings
MESSAGE_WINDOW_SIZE = None
TOKEN_LIMIT = None


def get_model() -> BaseModelBackend:
    """Get the model for general tasks."""
    return ModelFactory.create(
        model_platform=PLATFORM_TYPE,
        model_type=MODEL_TYPE,
        url = "https://openrouter.ai/api/v1",
        api_key=os.getenv("OPENROUTER_API_KEY"),  # Set your API key if needed
        model_config_dict=MODEL_CONFIG
    )

def get_worker_model() -> BaseModelBackend:
    """Get the model for worker agent."""
    return ModelFactory.create(
        model_platform=PLATFORM_TYPE,
        model_type=MODEL_TYPE_WORKER,
        url = "https://openrouter.ai/api/v1",
        api_key=os.getenv("OPENROUTER_API_KEY"),  # Set your API key if needed
        model_config_dict=WORKER_MODEL_CONFIG
    )

def get_web_model() -> BaseModelBackend:
    """Get the model for worker agent."""
    return ModelFactory.create(
        model_platform=PLATFORM_TYPE,
        model_type=MODEL_TYPE_WEB,
        url = "https://openrouter.ai/api/v1",
        api_key=os.getenv("OPENROUTER_API_KEY"),  # Set your API key if needed
        model_config_dict=WEB_MODEL_CONFIG
    )

def get_web_planning_model() -> BaseModelBackend:
    """Get the model for worker agent."""
    return ModelFactory.create(
        model_platform=PLATFORM_TYPE,
        model_type=MODEL_TYPE_WEB_PLANNING,
        url = "https://openrouter.ai/api/v1",
        api_key=os.getenv("OPENROUTER_API_KEY"),  # Set your API key if needed
        model_config_dict=WEB_PLANNING_MODEL_CONFIG
    )

def get_image_model() -> BaseModelBackend:
    """Get the model for worker agent."""
    return ModelFactory.create(
        model_platform=PLATFORM_TYPE,
        model_type=MODEL_TYPE_IMAGE,
        url = "https://openrouter.ai/api/v1",
        api_key=os.getenv("OPENROUTER_API_KEY"),  # Set your API key if needed
        model_config_dict=IMAGE_MODEL_CONFIG
    )

def get_audio_model() -> BaseModelBackend:
    """Get the model for worker agent."""
    return ModelFactory.create(
        model_platform=PLATFORM_TYPE,
        model_type=MODEL_TYPE_AUDIO,
        url = "https://openrouter.ai/api/v1",
        api_key=os.getenv("OPENROUTER_API_KEY"),  # Set your API key if needed
        model_config_dict=AUDIO_MODEL_CONFIG
    )







