import asyncio
import warnings
from typing import List

from camel.toolkits import (
    VideoAnalysisToolkit,
    SearchToolkit,
    CodeExecutionToolkit,
    ImageAnalysisToolkit,
    DocumentProcessingToolkit,
    AudioAnalysisToolkit,
    AsyncBrowserToolkit,
    ExcelToolkit,
    FunctionTool
)

from utils.config import get_model, get_worker_model, get_image_model, get_audio_model
from utils.task_information import get_task_information_context
from utils.agent_factory import create_agent, setup_mcp_toolkit, run_agent_loop
from dotenv import load_dotenv
from utils.prompts import get_user_message, get_tools_description



# Suppress parameter description warnings from toolkits
warnings.filterwarnings("ignore", message="Parameter description is missing.*", category=UserWarning)


# Model Configuration optimized for reasoning and problem solving
MODEL_CONFIG = {
   # "max_completion_tokens": 8192,
    "reasoning_effort": "high",
    # Note: O3_MINI doesn't support temperature, top_p, etc. due to reasoning model limitations
}


def get_system_message(tools_description_str: str) -> str:
    """Get the system message for the documentation_processing_agent."""
    return f"""
    ===== RULES OF DOCUMENTATION PROCESSING AGENT =====
    You are an advanced `document_processing_agent` powered by documentation processing capabilities and working within the Coral server ecosystem.

    Core Capabilities:
    1. Process Documents and Multimodal Data
       - Call `documentation_assistant` to approach all documentation-processed-based tasks.
    
    2. Agent Communication:
       - Use list_agents to check available agents
       - Use wait_for_mentions to receive messages
       - Use chat tools to communicate with the other agent
    
    If you simply need more information, don't overthink it, just ask other agents for help then try again when they respond.

    Available Tools:
    {tools_description_str}

    {get_task_information_context()}
    """

def create_documentation_processing_assistant():

    sys_msg = f"""
            You are a helpful assistant that can process documents and multimodal data, such as images, audio, and video.
            """

    model = get_worker_model()
    image_analysis_model = get_image_model()
    audio_reasoning_model = get_audio_model()

    document_processing_toolkit = DocumentProcessingToolkit(cache_dir="tmp")
    image_analysis_toolkit = ImageAnalysisToolkit(model=image_analysis_model)
    video_analysis_toolkit = VideoAnalysisToolkit(working_directory="tmp/video")
    audio_analysis_toolkit = AudioAnalysisToolkit(cache_dir="tmp/audio", audio_reasoning_model=audio_reasoning_model)
    code_runner_toolkit = CodeExecutionToolkit(sandbox="subprocess", verbose=True)

    tools=[
            FunctionTool(document_processing_toolkit.extract_document_content),
            FunctionTool(image_analysis_toolkit.ask_question_about_image),
            FunctionTool(audio_analysis_toolkit.ask_question_about_audio),
            FunctionTool(video_analysis_toolkit.ask_question_about_video),
            FunctionTool(code_runner_toolkit.execute_code),
        ]

    from camel.agents import ChatAgent

    camel_agent = ChatAgent(
        system_message=sys_msg,
        model=model,
        tools=tools,
    )

    return camel_agent

camel_documentation_processing_assistant = create_documentation_processing_assistant()

def documentation_processing_assistant(task: str) -> str:
    """
    Use this tool to approach documentation-related task from the documentation_processing_assistant.

    Args:
        task (str): The documentation-related task needs to be approached.

    Returns:
        str: The first message (msg0) from the agent's response.
    """
    resp = camel_documentation_processing_assistant.step(task)
    msg0 = resp.msgs[0]
    # Try to get content if it exists, otherwise convert to str
    if hasattr(msg0, "content"):
        return msg0.content
    return str(msg0)

def get_tools() -> List[FunctionTool]:
    """Get all tools."""
    tools = [
        FunctionTool(documentation_processing_assistant)
    ]
    return tools

async def create_documentation_processing_agent(connected_mcp_toolkit):
    """Create and initialize the documentation_processing_agent."""

    model = get_model()
    print("Model created successfully")

    print("🔄 Loading local documentation_processing tools...")
    tools = get_tools()
    print(f"✅ Loaded {len(tools)} local tools")

    # Use the agent factory to create the agent
    agent = await create_agent(
        agent_name="documentation_processing_agent",
        system_message_generator=get_system_message,
        model=model,
        mcp_toolkit=connected_mcp_toolkit,
        agent_specific_tools=tools,
        system_message_args={"tools_description_str": get_tools_description()}
    )

    print("documentation_processing_agent created successfully with reasoning capabilities.")
    return agent

async def main():
    print("Initializing documentation_processing_agent...")

    agent_id_param = "documentation_processing_agent"
    agent_description = "This agent is a helpful assistant that can process a variety of local and remote documents, including pdf, docx, images, audio, and video, etc."

    # Setup MCP toolkit
    connected_mcp_toolkit = await setup_mcp_toolkit(agent_id_param, agent_description)

    try:
        # Create agent
        agent = await create_documentation_processing_agent(connected_mcp_toolkit)

        # Run agent loop
        await run_agent_loop(
            agent=agent,
            agent_id=agent_id_param,
            # initial_prompt=initial_prompt,
            loop_prompt=get_user_message() + "(You are documentation_processing_agent)",
            max_iterations=10,
            sleep_time=5
        )

    except Exception as e:
        print(f"Error during documentation_processing_agent operation: {repr(e)}")
    finally:
        if connected_mcp_toolkit:
            await connected_mcp_toolkit.disconnect()
        print(f"Disconnecting {agent_id_param}...")
        print("Disconnected.")

if __name__ == "__main__":
    asyncio.run(main()) 
