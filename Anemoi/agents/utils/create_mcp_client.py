import os

from camel.toolkits.mcp_toolkit import MCPClient
from camel.utils.mcp_client import ServerConfig


def create_mcp_client(
    server_url: str = None,
    agent_id: str = "search_agent",
    agent_description: str = "Not described!",
    timeout: float = 300.0
) -> MCPClient:
    """Create and return an MCP client instance."""
    if agent_description == "Not described!":
        print("Warning: Agent description is not provided. Using default 'Not described!'.")
    modified_server_url = os.getenv("CORAL_CONNECTION_URL") or f"http://localhost:5555/devmode/gaia/public/cd9fa356-a7cb-4a1d-b925-e63a111cf90c/sse?agentId=web"
    # client = MCPClient(server_url, timeout=timeout)
    if not modified_server_url:
        raise ValueError("Server URL must be provided via the CORAL_CONNECTION_URL environment variable.")
    # url encode agent description
    from urllib.parse import quote
    agent_description = quote(agent_description)
    

    # ##################debug##################
    # import urllib.parse
    # coral_params = {
    #     "agentId": agent_id,
    #     "agentDescription": agent_description
    # }

    # query_string = urllib.parse.urlencode(coral_params)
    # base_url="http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
    # modified_server_url = f"{base_url}?{query_string}"
    # ###########################################

    print(f"Connecting to MCP server at {modified_server_url} with description '{agent_description}'")
    return MCPClient(ServerConfig(url = modified_server_url, timeout=timeout, sse_read_timeout=timeout, terminate_on_close=True, prefer_sse=True), timeout=timeout)
