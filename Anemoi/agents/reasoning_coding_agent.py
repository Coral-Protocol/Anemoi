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
    """Get the system message for the reasoning_coding_agent."""
    return f"""
    ===== RULES OF REASONING CODING AGENT =====
    You are an advanced `reasoning_coding_agent` powered by reasoning and coding capabilities and working within the Coral server ecosystem.

    Core Capabilities:
    1. Reasoning and Coding
       - Call `reasoning_coding_assistant` to approach all reasoning and coding-based tasks.
    
    2. Agent Communication:
       - Use list_agents to check available agents
       - Use wait_for_mentions to receive messages
       - Use chat tools to communicate with the other agent
    
    If you simply need more information, don't overthink it, just ask other agents for help then try again when they respond.

    Available Tools:
    {tools_description_str}

    {get_task_information_context()}
    """

def create_reasoning_coding_assistant():

    sys_msg = f"""
            You are a helpful assistant that specializes in reasoning and coding, and can think step by step to solve the task. 
            When necessary, you can write python code to solve the task. If you have written code, do not forget to execute the code. 
            Never generate codes like 'example code', your code should be able to fully solve the task. You can also leverage multiple libraries, 
            such as requests, BeautifulSoup, re, pandas, etc, to solve the task. For processing excel files, you should write codes to process them.
            """

    model = get_worker_model()

    document_processing_toolkit = DocumentProcessingToolkit(cache_dir="tmp")
    code_runner_toolkit = CodeExecutionToolkit(sandbox="subprocess", verbose=True)
    excel_toolkit = ExcelToolkit()

    tools=[
            FunctionTool(code_runner_toolkit.execute_code),
            FunctionTool(excel_toolkit.extract_excel_content),
            FunctionTool(document_processing_toolkit.extract_document_content),
        ]

    from camel.agents import ChatAgent

    camel_agent = ChatAgent(
        system_message=sys_msg,
        model=model,
        tools=tools,
    )

    return camel_agent

camel_reasoning_coding_assistant = create_reasoning_coding_assistant()

def reasoning_coding_assistant(task: str) -> str:
    """
    Use this tool to approach reasoning and coding-related task from the reasoning_coding_assistant.

    Args:
        task (str): The reasoning and coding-related task needs to be approached.

    Returns:
        str: The first message (msg0) from the agent's response.
    """
    resp = camel_reasoning_coding_assistant.step(task)
    msg0 = resp.msgs[0]
    # Try to get content if it exists, otherwise convert to str
    if hasattr(msg0, "content"):
        return msg0.content
    return str(msg0)

def get_tools() -> List[FunctionTool]:
    """Get all tools."""
    tools = [
        FunctionTool(reasoning_coding_assistant)
    ]
    return tools

async def create_reasoning_coding_agent(connected_mcp_toolkit):
    """Create and initialize the reasoning_coding_agent."""

    model = get_model()
    print("Model created successfully")

    print("🔄 Loading local reasoning_coding tools...")
    tools = get_tools()
    print(f"✅ Loaded {len(tools)} local tools")

    # Use the agent factory to create the agent
    agent = await create_agent(
        agent_name="reasoning_coding_agent",
        system_message_generator=get_system_message,
        model=model,
        mcp_toolkit=connected_mcp_toolkit,
        agent_specific_tools=tools,
        system_message_args={"tools_description_str": get_tools_description()}
    )

    print("reasoning_coding_agent created successfully with reasoning capabilities.")
    return agent

async def main():
    print("Initializing reasoning_coding_agent...")

    agent_id_param = "reasoning_coding_agent"
    agent_description = "This agent is a helpful assistant that specializes in reasoning, coding, and processing excel files. However, it cannot access the internet to search for information. If the task requires python execution, it should be informed to execute the code after writing it."

    # Setup MCP toolkit
    connected_mcp_toolkit = await setup_mcp_toolkit(agent_id_param, agent_description)

    try:
        # Create agent
        agent = await create_reasoning_coding_agent(connected_mcp_toolkit)

        # Run agent loop
        await run_agent_loop(
            agent=agent,
            agent_id=agent_id_param,
            # initial_prompt=initial_prompt,
            loop_prompt=get_user_message() + "(You are reasoning_coding_agent)",
            max_iterations=10,
            sleep_time=5
        )

    except Exception as e:
        print(f"Error during reasoning_coding_agent operation: {repr(e)}")
    finally:
        if connected_mcp_toolkit:
            await connected_mcp_toolkit.disconnect()
        print(f"Disconnecting {agent_id_param}...")
        print("Disconnected.")

if __name__ == "__main__":
    asyncio.run(main()) 
