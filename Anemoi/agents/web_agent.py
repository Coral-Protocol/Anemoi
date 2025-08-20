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

from utils.config import get_model, get_worker_model, get_web_model, get_web_planning_model
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
    """Get the system message for the web agent."""
    return f"""
    ===== RULES OF WEB AGENT =====
    You are an advanced `web_agent` powered by web browsing/searching capabilities and working within the Coral server ecosystem.

    Core Capabilities:
    1. Web Browsing and Searching 
       - Call `web_assistant` to approach all web-based tasks.
    
    2. Agent Communication:
       - Use list_agents to check available agents
       - Use wait_for_mentions to receive messages
       - Use chat tools to communicate with the other agent
    
    If you simply need more information, don't overthink it, just ask other agents for help then try again when they respond.

    Available Tools:
    {tools_description_str}

    {get_task_information_context()}
    """

async def create_web_assistant():

    sys_msg = f"""
            You are a helpful assistant that can search the web, extract webpage content, simulate browser actions, and provide relevant information to solve the given task.
            Keep in mind that:
            - Do not be overly confident in your own knowledge. Searching can provide a broader perspective and help validate existing knowledge.  
            - If one way fails to provide an answer, try other ways or methods. The answer does exists.
            - If the search snippet is unhelpful but the URL comes from an authoritative source, try visit the website for more details.  
            - When looking for specific numerical values (e.g., dollar amounts), prioritize reliable sources and avoid relying only on search snippets.  
            - When solving tasks that require web searches, check Wikipedia first before exploring other websites.  
            - You can also simulate browser actions to get more information or verify the information you have found.
            - Browser simulation is also helpful for finding target URLs. Browser simulation operations do not necessarily need to find specific answers, but can also help find web page URLs that contain answers (usually difficult to find through simple web searches). You can find the answer to the question by performing subsequent operations on the URL, such as extracting the content of the webpage.
            - Do not solely rely on document tools or browser simulation to find the answer, you should combine document tools and browser simulation to comprehensively process web page information. Some content may need to do browser simulation to get, or some content is rendered by javascript.
            - In your response, you should mention the urls you have visited and processed.

            Here are some tips that help you perform web search:
            - Never add too many keywords in your search query! Some detailed results need to perform browser interaction to get, not using search toolkit.
            - If the question is complex, search results typically do not provide precise answers. It is not likely to find the answer directly using search toolkit only, the search query should be concise and focuses on finding official sources rather than direct answers.
            For example, as for the question "What is the maximum length in meters of #9 in the first National Geographic short on YouTube that was ever released according to the Monterey Bay Aquarium website?", your first search term must be coarse-grained like "National Geographic YouTube" to find the youtube website first, and then try other fine-grained search terms step-by-step to find more urls.
            - The results you return do not have to directly answer the original question, you only need to collect relevant information.
            """

    model = get_worker_model()
    web_model = get_web_model()
    web_planning_model = get_web_planning_model()

    search_toolkit = SearchToolkit()
    document_processing_toolkit = DocumentProcessingToolkit(cache_dir="tmp")
    video_analysis_toolkit = VideoAnalysisToolkit(working_directory="tmp/video")
    browser_simulator_toolkit = AsyncBrowserToolkit(headless=True, cache_dir="tmp/browser", planning_agent_model=web_planning_model, web_agent_model=web_model)

    tools=[
            FunctionTool(search_toolkit.search_google),
            FunctionTool(search_toolkit.search_wiki),
            FunctionTool(search_toolkit.search_wiki_revisions),
            FunctionTool(search_toolkit.search_archived_webpage),
            FunctionTool(document_processing_toolkit.extract_document_content),
            FunctionTool(browser_simulator_toolkit.browse_url),
            FunctionTool(video_analysis_toolkit.ask_question_about_video),
        ]

    from camel.agents import ChatAgent

    camel_agent = ChatAgent(
        system_message=sys_msg,
        model=model,
        tools=tools,
    )

    return camel_agent



async def create_web_agent(connected_mcp_toolkit, tools):
    """Create and initialize the web agent."""

    model = get_model()
    print("Model created successfully")

    # Use the agent factory to create the agent
    agent = await create_agent(
        agent_name="web_agent",
        system_message_generator=get_system_message,
        model=model,
        mcp_toolkit=connected_mcp_toolkit,
        agent_specific_tools=tools,
        system_message_args={"tools_description_str": get_tools_description()}
    )

    print("web_agent created successfully with reasoning capabilities.")
    return agent

async def main():
    print("Initializing web_agent...")

    agent_id_param = "web_agent"
    agent_description = "This agent is a helpful assistant that can search the web, extract webpage content, simulate browser actions, and retrieve relevant information."

    # Setup MCP toolkit
    connected_mcp_toolkit = await setup_mcp_toolkit(agent_id_param, agent_description)

    camel_web_assistant = await create_web_assistant()

    async def web_assistant(task: str) -> str:
        """
        Use this tool to approach web-related task from the web_assistant.

        Args:
            task (str): The web_related task needs to be approached.

        Returns:
            str: The first message (msg0) from the agent's response.
        """
        resp = await camel_web_assistant.astep(task)
        msg0 = resp.msgs[0]
        # Try to get content if it exists, otherwise convert to str
        if hasattr(msg0, "content"):
            return msg0.content
        return str(msg0)

    tools = [FunctionTool(web_assistant)]

    try:
        # Create agent
        agent = await create_web_agent(connected_mcp_toolkit, tools)

        # Run agent loop
        await run_agent_loop(
            agent=agent,
            agent_id=agent_id_param,
            # initial_prompt=initial_prompt,
            loop_prompt=get_user_message() + "(You are web_agent)",
            max_iterations=10,
            sleep_time=5
        )

    except Exception as e:
        print(f"Error during web_agent operation: {repr(e)}")
    finally:
        if connected_mcp_toolkit:
            await connected_mcp_toolkit.disconnect()
        print(f"Disconnecting {agent_id_param}...")
        print("Disconnected.")

if __name__ == "__main__":
    asyncio.run(main()) 
