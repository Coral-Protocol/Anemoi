import asyncio

from camel.toolkits import MCPToolkit

from utils.prompts import get_tools_description
from utils.config import get_model
from utils.SendAnswerTool import SendAnswerToolkit
from utils.task_information import get_task_context_for_answer_capable_agent
from utils.agent_factory import create_agent, setup_mcp_toolkit, run_agent_loop
from dotenv import load_dotenv

from utils.config import MODEL_CONFIG

from utils.prompts import get_user_message

# Fix for Pydantic warnings - apply to model_config where needed
MODEL_CONFIG_WITH_FIX = MODEL_CONFIG.copy() if isinstance(MODEL_CONFIG, dict) else {}

def get_system_message() -> str:
    """Get the system message for the assistant agent."""
    return f"""
You identify as "answer_finding_agent" and collaborate with other agents to complete tasks.

{get_task_context_for_answer_capable_agent()}

Ensure to call the send_answer tool when you have the final solution to a task.
{get_tools_description()}

ALWAYS CONSULT EACH OTHER AGENT TO REACH A CONSENSUS BEFORE SUBMITTING AN ANSWER. If you disagree, take it to a vote. Submit each agent's vote and reasoning (ask them for it with their vote) as evidence and ensure you have a majority of votes counted before submitting the answer.
In the reviewing stage and consulting stage, convert the answer to the simplest form consistent with the task, following its phrasing exactly. Examples of conversions are:

```
Banana count -> Bananas
120km -> 120
"100.0" -> 100.0
The answer is 40°C -> 40
(When the task asks "how many hundreds of x" and you find x is 500) 500 -> 5
```

Allow 4 minutes for the voting process though and if a majority is not reached, submit the answer with the most votes, including all evidence and reasoning from each agent in the justifications. Remember that some lengthy coding or analysis tasks may take longer, so be patient and allow enough time for all agents to respond.

Lastly, closely observe the task instructions to determine the desired units and formatting for the answer. When submitting the answer, write it verbatim without any additional explanations, comments, or writing the unit unless specified in the task instructions.
    """

def get_assistant_tools() -> list[SendAnswerToolkit]:
    assistant_tools = [
        *SendAnswerToolkit().get_tools()
    ]

    return assistant_tools

async def create_answer_finding_agent(mcp_toolkit_instance: MCPToolkit):
    """Create and initialize the answer finding agent."""
    model = get_model()

    # Get agent-specific tools
    assistant_specific_tools = get_assistant_tools()

    # Use the agent factory to create the agent
    agent = await create_agent(
        agent_name="answer_finding_agent",
        system_message_generator=get_system_message,
        model=model,
        mcp_toolkit=mcp_toolkit_instance,
        agent_specific_tools=assistant_specific_tools
    )

    return agent


async def main():
    print("Initializing answer_finding_agent...")

    agent_id_param = "answer_finding_agent"
    agent_description = "Answer Finding Agent which specializes in finding and submitting answers to tasks."

    # Setup MCP toolkit
    connected_mcp_toolkit = await setup_mcp_toolkit(agent_id_param, agent_description)

    try:
        # Create agent
        agent = await create_answer_finding_agent(connected_mcp_toolkit)
        print("Answer_finding_agent created successfully.")

        # Initial prompt
        initial_prompt = f"{agent_id_param} initialized. Ready to find and submit answers to tasks."

        # Run agent loop
        await run_agent_loop(
            agent=agent,
            agent_id=agent_id_param,
            initial_prompt=initial_prompt,
            loop_prompt=get_user_message(),
            max_iterations=30,
            sleep_time=5
        )

    except Exception as e:
        print(f"Error during answer_finding_agent operation: {repr(e)}")
        raise e
    finally:
        if connected_mcp_toolkit:
            await connected_mcp_toolkit.disconnect()
        print(f"Disconnecting {agent_id_param}...")
        print("Disconnected.")

if __name__ == "__main__":
    # os.environ["TASK_INSTRUCTION"] = "Get to know the other agents"
    asyncio.run(main())
