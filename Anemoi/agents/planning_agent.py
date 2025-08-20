import asyncio
import os
import time
from asyncio import sleep
from typing import Dict, Any, List

from camel.toolkits import MCPToolkit
from camel.toolkits.base import FunctionTool
from dotenv import load_dotenv

from utils.config import MESSAGE_WINDOW_SIZE, TOKEN_LIMIT, get_model, get_worker_model
from utils.prompts import get_user_message, get_tools_description
from utils.agent_factory import create_agent, setup_mcp_toolkit

def get_system_message() -> str:
    """Get the system message for the planning agent, including dynamic tool descriptions."""
    tools_desc_str = get_tools_description()
    return f"""
    ===== RULES OF PLANNING AGENT =====
    You are a specialized planning agent responsible for coordinating complex tasks.
    You identify as "planning_agent" and work with the other agents through the Coral server.

    Core Responsibilities:
    1. Task Planning:
       - Break down complex tasks into manageable steps
       - Create detailed action plans
       - Monitor task progress
       - Adjust plans based on feedback

    2. Agent Communication:
       - Use list_agents to check available agents
       - Use wait_for_mentions to receive messages
       - Use chat tools to communicate with the other agent

    3. Task Coordination:
       - Provide step-by-step instructions to the web agent
       - Monitor the web agent's progress
       - Handle task deviations and errors
       - Ensure task completion
    
    Planning Guidelines:
    1. Task Analysis:
       - Understand the overall objective
       - Identify required resources
       - Consider potential obstacles
       - Plan for contingencies

    2. Step Creation:
       - Break tasks into atomic steps
       - Define clear success criteria
       - Include verification steps
       - Plan for error recovery

    3. Progress Monitoring:
       - Track step completion
       - Verify step success
       - Identify bottlenecks
       - Adjust plans as needed

    Communication Rules:
    1. Always use appropriate tools:
       - Use tools to receive tasks, communicate with other agents (e.g., web_agent), and manage plans.
       - Provide clear instructions when delegating tasks.
       - Request status updates as needed.
       - Give constructive feedback.

    2. Response Guidelines:
       - Provide structured plans
       - Include success criteria
       - Document assumptions
       - Report progress clearly

    Remember:
    - You are part of a multi-agent system
    - Always verify task completion
    - Document your plans
    - Handle errors gracefully
    - Keep track of progress
    ALWAYS CONSULT EACH OTHER AGENT TO REACH A CONSENSUS BEFORE SUBMITTING AN ANSWER. If you disagree, take it to a vote.
    
    If the appropriate for the plan, you should encourage other agents to wait for web_agent before speculating about the answer, as they will be able to find the most accurate information.
    
    ===== AVAILABLE TOOLS =====
{tools_desc_str}
    """

def get_planning_tools() -> List[FunctionTool]:
    """Get the list of available tools for planning."""
    return [
        # FunctionTool(planning_assistant),
        # FunctionTool(create_plan),
        # FunctionTool(monitor_progress),
        # FunctionTool(adjust_plan)
    ]

async def create_planning_agent(
    connected_mcp_toolkit: MCPToolkit,
):
    """Create and return a ChatAgent instance with planning capabilities."""

    print("Creating planning_agent with MCP and planning tools...")

    # Get agent-specific tools
    agent_specific_tools = get_planning_tools()

    # Get model
    model = get_model()

    # Use the agent factory to create the agent
    agent = await create_agent(
        agent_name="planning_agent",
        system_message_generator=get_system_message,
        model=model,
        mcp_toolkit=connected_mcp_toolkit,
        agent_specific_tools=agent_specific_tools,
        message_window_size=MESSAGE_WINDOW_SIZE,
        token_limit=TOKEN_LIMIT
    )

    return agent

async def run_planning_agent(
    max_iterations: int = 20,
    sleep_time: int = 10,
):
    """Run the planning agent."""
    print("Initializing planning_agent...")

    agent_id_param = "planning_agent"
    agent_description = "Planning Agent which specializes in coordinating complex tasks"

    # Setup MCP toolkit
    connected_mcp_toolkit = await setup_mcp_toolkit(agent_id_param, agent_description)

    try:
        # Create agent
        agent = await create_planning_agent(connected_mcp_toolkit)
        print("Planning_agent created successfully.")

        # Custom loop function for planning agent
        async def custom_loop_handler(agent, loop_prompt):
            resp = await agent.astep(loop_prompt)

            if resp and resp.msgs:
                msg_content = resp.msgs[0].content
                print(f"Planning_agent raw received message content: {msg_content}")

            return resp


        # Initial prompt
        initial_prompt = f"[automated]this is a pre-recorded message/instruction. Begin planning with other agents to achieve our task/answer our query described earlier. " \
                            f"Create a thread with all agents initially to keep the team aligned, if you don't think an agent will be needed" \
                            f" don't add them. Err on the side of adding an agent to the thread if you're unsure" \
                             f"At the start you should analyze the instructions to figure out exactly what is needed." \
                            f"You should start by identifying the unit and formatting to assume. For example, if the instructions specify to give it in 10s of kilometres, and you eventually find the answer to be 155km, the output should just be '15.5', and you should communicate this to the others." \
                            f"Share the task phrasing verbatim before starting to break it down for the team to work on it. You can share it in a code block, as messages support multiple lines." \
                            f"From now you will be on a loop and not receive further instructions from 'me'." \
                            f"For reference, here is the instructions you were given, remember to share it with the others: {os.getenv('TASK_INSTRUCTION', 'No instructions provided')}\n"


        await agent.astep(initial_prompt)
        # Run agent loop with custom handler
        iteration_count = 0
        while iteration_count < max_iterations:
            try:
                await custom_loop_handler(agent, get_user_message())
                await asyncio.sleep(sleep_time)
                iteration_count += 1
            except Exception as e:
                print(f"Error in planning_agent loop: {repr(e)}")
                await asyncio.sleep(sleep_time)
                iteration_count += 1

        print("Planning_agent finished its run.")

    except Exception as e:
        print(f"Error during planning_agent operation: {repr(e)}")
        # print stack trace for debugging
        import traceback
        traceback.print_exc()
    finally:
        if connected_mcp_toolkit:
            await connected_mcp_toolkit.disconnect()
        print(f"Disconnecting {agent_id_param}...")
        print("Disconnected.")

async def main():
    """Main entry point for the planning agent."""
    await run_planning_agent()

if __name__ == "__main__":
    asyncio.run(main())
