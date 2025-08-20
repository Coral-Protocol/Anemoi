"""
Agent Factory Module

This module provides utilities for creating and running agents in a standardized way.
It helps reduce code duplication across different agent implementations.
"""

import asyncio
from typing import Any, Callable, Dict, List, Optional, Type, Union

from camel.agents import ChatAgent
from camel.messages import BaseMessage
from camel.models import BaseModelBackend
from camel.responses import ChatAgentResponse
from camel.toolkits import FunctionTool, MCPToolkit
from camel.toolkits.base import FunctionTool

from .chat_agent_with_resources import MCPResourceAgent
from  .create_mcp_client import create_mcp_client
from .config import MESSAGE_WINDOW_SIZE, TOKEN_LIMIT


async def create_agent(
    agent_name: str,
    system_message_generator: Callable,
    model: BaseModelBackend,
    mcp_toolkit: MCPToolkit,
    agent_specific_tools: List[FunctionTool] = None,
    system_message_args: Dict[str, Any] = None,
    message_window_size: int = None,
    token_limit: int = None,
    debug: bool = False
) -> MCPResourceAgent:
    """
    Create and initialize an agent with standardized configuration.
    
    Args:
        agent_name: Name of the agent
        system_message_generator: Function that generates the system message
        model: Model backend to use
        mcp_toolkit: MCP toolkit instance
        agent_specific_tools: List of agent-specific tools
        system_message_args: Arguments to pass to the system message generator
        message_window_size: Size of the message window
        token_limit: Token limit for the agent
        
    Returns:
        Initialized MCPResourceAgent
    """
    print(f"Creating {agent_name}...")
    
    # Get MCP tools
    mcp_tools = mcp_toolkit.get_tools() if mcp_toolkit else []
    print(f"✅ Loaded {len(mcp_tools)} MCP tools")
    
    # Get agent-specific tools
    agent_specific_tools = agent_specific_tools or []
    print(f"✅ Loaded {len(agent_specific_tools)} agent-specific tools")
    
    # Combine tools
    all_tools = mcp_tools + agent_specific_tools
    
    # Generate system message
    system_message_args = system_message_args or {}
    system_message = system_message_generator(**system_message_args)
    
    # Create agent
    agent = MCPResourceAgent(
        system_message=system_message,
        model=model,
        tools=all_tools,
        message_window_size=message_window_size,
        token_limit=token_limit,
        mcp_toolkit=mcp_toolkit,
        debug=debug
    )
    
    print(f"{agent_name} created successfully.")
    return agent


async def setup_mcp_toolkit(
    agent_id: str,
    agent_description: str = None,
    timeout: float = 300.0
) -> MCPToolkit:
    """
    Set up the MCP toolkit with a client.
    
    Args:
        agent_id: ID of the agent
        agent_description: Description of the agent
        timeout: Timeout for the MCP client
        
    Returns:
        Connected MCP toolkit
    """
    print(f"Setting up MCP toolkit for {agent_id}...")
    
    # Create MCP client
    coral_server = create_mcp_client(
        agent_id=agent_id,
        agent_description=agent_description,
        timeout=3000.0
    )
    
    # Create MCP toolkit
    mcp_toolkit = MCPToolkit([coral_server])
    
    # Connect to MCP server
    await coral_server.__aenter__()
    print(f"Connected to MCP server as {agent_id}")
    
    return mcp_toolkit


async def run_agent_loop(
    agent: ChatAgent,
    agent_id: str,
    initial_prompt: str = None,
    loop_prompt: str = None,
    max_iterations: int = 20,
    sleep_time: int = 5
) -> None:
    """
    Run the agent in a loop.
    
    Args:
        agent: The agent to run
        agent_id: ID of the agent
        initial_prompt: Initial prompt to send to the agent
        loop_prompt: Prompt to send to the agent in each loop iteration
        max_iterations: Maximum number of iterations
        sleep_time: Time to sleep between iterations
    """
    max_iterations = 20
    # Send initial prompt if provided
    if initial_prompt:
        print(f"Sending initial prompt to {agent_id}...")
        response = await agent.astep(initial_prompt)
        print(f"{agent_id} initial response:")
        if response and response.msgs:
            print(response.msgs[0].content)
        else:
            print("No response received for initial prompt.")
    
    # Run loop
    print(f"\n--- {agent_id} entering loop (will run for {max_iterations} iterations) ---")
    for i in range(max_iterations):
        print(f"\nLoop iteration {i+1}/{max_iterations}")
        try:
            if not loop_prompt:
                print(f"No loop prompt provided for {agent_id}, skipping iteration.")
                continue
                
            loop_response = await agent.astep(loop_prompt)
            print(f"{agent_id} loop response:")
            if loop_response and loop_response.msgs:
                print(loop_response.msgs[0].content)
            else:
                print("No response received in this loop iteration.")
        except Exception as loop_e:
            print(f"Error during loop iteration: {loop_e}")
        
        await asyncio.sleep(sleep_time)