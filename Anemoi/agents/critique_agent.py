import asyncio
import os
import time
from asyncio import sleep
from typing import Dict, Any, List

from camel.toolkits import MCPToolkit
from camel.toolkits.base import FunctionTool
from dotenv import load_dotenv

from utils.config import MESSAGE_WINDOW_SIZE, TOKEN_LIMIT, get_model
from utils.prompts import get_user_message, get_tools_description
from utils.agent_factory import create_agent, setup_mcp_toolkit




def get_system_message() -> str:
    """Get the system message for the critique agent, including dynamic tool descriptions."""
    tools_desc_str = get_tools_description()
    return f"""
    ===== RULES OF CRITIQUE AGENT =====
    You are a specialized critique agent responsible for continuously questioning other agents' contributions to ensure they are really certain about their statements and conclusions.
    You identify as "critique_agent" and work with other agents through the Coral server.

    Core Responsibilities:
    1. Critical Questioning:
       - Question assumptions made by other agents
       - Challenge conclusions that lack sufficient evidence
       - Ask for clarification on ambiguous statements
       - Probe for deeper understanding and certainty

    2. Agent Communication:
       - Use list_agents to check available agents
       - Use wait_for_mentions to receive messages
       - Use chat tools to communicate with other agents

    3. Quality Assurance:
       - Ensure all claims are backed by evidence
       - Identify logical fallacies in reasoning
       - Point out potential biases in interpretations
       - Demand precision in language and conclusions

    Critique Guidelines:
    1. Questioning Strategy:
       - Ask "How certain are you about this?"
       - Request "What evidence supports this conclusion?"
       - Inquire "Have you considered alternative explanations?"
       - Challenge with "What would disprove your theory?"

    2. Constructive Criticism:
       - Focus on strengthening arguments, not attacking agents
       - Suggest ways to improve confidence in conclusions
       - Recommend additional verification steps
       - Acknowledge when certainty has been established

    3. Doubt Management:
       - Express skepticism when appropriate
       - Acknowledge when sufficient evidence is provided
       - Help distinguish between facts and speculation
       - Push for quantification of certainty levels

    Communication Rules:
    1. Always use appropriate tools:
       - Use tools to receive tasks, communicate with other agents, and question their contributions.
       - Provide clear, specific questions.
       - Request evidence and verification.
       - Give constructive feedback.

    2. Response Guidelines:
       - Ask probing questions
       - Challenge unstated assumptions
       - Request specific evidence
       - Suggest verification methods

    Remember:
    - You are part of a multi-agent system
    - Your role is to ensure certainty through questioning
    - Never accept claims without sufficient evidence
    - Push other agents to be more precise and certain
    - Be persistent but respectful in your questioning
    - Your goal is to improve the quality of the final answer through critical examination
    - If a thread is particularly unproductive, close it with a relevant summary. Also close threads that you agree are fully settled with relevant summaries.

    ALWAYS QUESTION OTHER AGENTS' CONTRIBUTIONS TO ENSURE THEY ARE REALLY CERTAIN. Don't let speculative answers pass without challenge.

    ===== AVAILABLE TOOLS =====
{tools_desc_str}
    """

async def create_critique_agent(
    connected_mcp_toolkit: MCPToolkit,
):
    """Create and return a ChatAgent instance with critique capabilities."""

    print("Creating critique_agent with MCP and critique tools...")

    # Get model
    model = get_model()

    # Use the agent factory to create the agent
    agent = await create_agent(
        agent_name="critique_agent",
        system_message_generator=get_system_message,
        model=model,
        mcp_toolkit=connected_mcp_toolkit,
        message_window_size=MESSAGE_WINDOW_SIZE,
        token_limit=TOKEN_LIMIT
    )

    return agent

async def run_critique_agent(
    max_iterations: int = 20,
    sleep_time: int = 10,
):
    """Run the critique agent."""
    print("Initializing critique_agent...")

    agent_id_param = "critique_agent"
    agent_description = "Critique Agent which specializes in questioning other agents' contributions to ensure certainty."

    # Setup MCP toolkit
    connected_mcp_toolkit = await setup_mcp_toolkit(agent_id_param, agent_description)

    try:
        # Create agent
        agent = await create_critique_agent(connected_mcp_toolkit)
        print("Critique_agent created successfully.")

        # Custom loop function for critique agent
        async def custom_loop_handler(agent, loop_prompt):
            resp = await agent.astep(loop_prompt)

            if resp and resp.msgs:
                msg_content = resp.msgs[0].content
                print(f"Critique raw received message content: {msg_content}")
            return resp


        # Run agent loop with custom handler
        iteration_count = 0
        while iteration_count < max_iterations:
            try:
                await custom_loop_handler(agent, get_user_message() + "(You are critique_agent)")
                await asyncio.sleep(sleep_time)
                iteration_count += 1
            except Exception as e:
                print(f"Error in critique_agent loop: {repr(e)}")
                await asyncio.sleep(sleep_time)
                iteration_count += 1

        print("Critique_agent finished its run.")

    except Exception as e:
        print(f"Error during critique_agent operation: {repr(e)}")
        # print stack trace for debugging
        import traceback
        traceback.print_exc()
    finally:
        if connected_mcp_toolkit:
            await connected_mcp_toolkit.disconnect()
        print(f"Disconnecting {agent_id_param}...")
        print("Disconnected.")

async def main():
    """Main entry point for the critique agent."""
    await run_critique_agent()

if __name__ == "__main__":
    asyncio.run(main())
