from __future__ import annotations

import asyncio
import json
import re
import threading
from typing import (
    TYPE_CHECKING,
    Any,
    Callable,
    Dict,
    List,
    Optional,
    Set,
    Tuple,
    Type,
    Union,
)

import openai
from camel.agents._types import ToolCallRequest, ModelResponse
from camel.types.agents import ToolCallingRecord
from camel.utils.mcp_client import MCPClient
from mcp import ClientSession
from openai.types.chat import ChatCompletion
from pydantic import BaseModel

from camel.agents import ChatAgent
from camel.agents.base import BaseAgent
from camel.memories import (
    AgentMemory,
    ChatHistoryMemory,
    MemoryRecord,
    ScoreBasedContextCreator,
)
from camel.messages import BaseMessage, OpenAIMessage
from camel.models import (
    BaseModelBackend,
    ModelFactory,
    ModelManager,
)
from camel.responses import ChatAgentResponse
from camel.toolkits import FunctionTool, MCPToolkit
from camel.types import (
    MCPRegistryType,
    ModelPlatformType,
    ModelType,
    OpenAIBackendRole,
    RoleType,
)

if TYPE_CHECKING:
    from camel.terminators import ResponseTerminator

# AgentOps decorator setting
try:
    import os

    if os.getenv("AGENTOPS_API_KEY") is not None:
        from agentops import track_agent
    else:
        raise ImportError
except (ImportError, AttributeError):
    from camel.utils import track_agent


@track_agent(name="MCPResourceAgent")
class MCPResourceAgent(ChatAgent):
    r"""A specialized ChatAgent that dynamically loads MCP resources into its
    system message.

    This agent extends ChatAgent by allowing the system message to contain MCP
    resource URLs (e.g., `<resource>coral://server_name/resource_path</resource>`).
    Before each step, these resources are fetched and their content is injected
    into the system message, ensuring the agent always has access to the most
    up-to-date information.

    The resource URLs should be wrapped in XML tags:
    ```
    <resource>coral://my_server/documents/guide.txt</resource>
    ```

    Attributes:
        mcp_toolkit (MCPToolkit): The MCP toolkit for fetching resources.
        resource_cache (Dict[str, str]): Cache for resource contents.
        **kwargs: All other attributes inherited from ChatAgent.

    Example:
        >>> # Create an agent with a system message containing MCP resources
        >>> system_msg = '''You are a helpful assistant.
        ...
        ... Here is the current documentation:
        ... <resource>coral://docs_server/api/latest.md</resource>
        ...
        ... And here are the current guidelines:
        ... <resource>coral://policy_server/guidelines.txt</resource>
        ... '''
        >>>
        >>> agent = await MCPResourceAgent.create(
        ...     system_message=system_msg,
        ...     mcp_config={"docs_server": {...}, "policy_server": {...}}
        ... )
        >>>
        >>> # The resources will be fetched and injected before each step
        >>> response = await agent.astep("What's in the documentation?")
    """

    def __init__(
            self,
            system_message: Optional[Union[str, BaseMessage]] = None,
            model: Optional[BaseModelBackend] = None,
            mcp_toolkit: Optional[MCPToolkit] = None,
            mcp_config: Optional[Dict[str, Any]] = None,
            memory: Optional[AgentMemory] = None,
            message_window_size: Optional[int] = None,
            token_limit: Optional[int] = None,
            output_language: Optional[str] = None,
            tools: Optional[List[Union[FunctionTool, Callable]]] = None,
            external_tools: Optional[
                List[Union[FunctionTool, Callable, Dict[str, Any]]]
            ] = None,
            response_terminators: Optional[List[ResponseTerminator]] = None,
            scheduling_strategy: str = "round_robin",
            max_iteration: Optional[int] = None,
            agent_id: Optional[str] = None,
            stop_event: Optional[threading.Event] = None,
            debug: bool = False,
    ) -> None:
        # Initialize MCP toolkit
        if mcp_toolkit:
            self.mcp_toolkit = mcp_toolkit
        elif mcp_config:
            self.mcp_toolkit = MCPToolkit(config_dict=mcp_config)
        else:
            self.mcp_toolkit = None

        # Store the template system message (with resource placeholders)
        self._template_system_message = system_message

        # Cache for resource contents
        self.resource_cache: Dict[str, str] = {}
        self.most_recent_message_jsons: List[str] = []


        # Get python current working directory
        import os
        current_working_directory = os.getcwd()
        print(f"Current working directory: {current_working_directory}")
        # Initialize parent with the system message (resources will be loaded on first step)
        super().__init__(
            system_message=system_message,
            model=model,
            memory=memory,
            message_window_size=60,
            token_limit=80000,
            output_language=output_language,
            tools=tools,
            external_tools=external_tools,
            response_terminators=response_terminators,
            scheduling_strategy=scheduling_strategy,
            max_iteration=10,
            agent_id=agent_id,
            stop_event=stop_event,
        )
        self.model_backend_real = self.model_backend.models[0]
        self.original_temperature = self.model_backend_real.model_config_dict['temperature']
        self.original_frequency_penalty = self.model_backend_real.model_config_dict['frequency_penalty']
        self.original_top_p = self.model_backend_real.model_config_dict['top_p']
        self.debug = debug


    @classmethod
    async def create(
            cls,
            system_message: Optional[Union[str, BaseMessage]] = None,
            model: Optional[BaseModelBackend] = None,
            mcp_config: Optional[Dict[str, Any]] = None,
            mcp_toolkit: Optional[MCPToolkit] = None,
            **kwargs,
    ) -> "MCPResourceAgent":
        r"""Create and connect an MCPResourceAgent instance.

        Args:
            system_message: System message that may contain MCP resource URLs.
            model: The model backend to use.
            mcp_config: Configuration dictionary for MCP servers.
            mcp_toolkit: Pre-configured MCP toolkit (alternative to mcp_config).
            **kwargs: Additional arguments passed to MCPResourceAgent constructor.

        Returns:
            MCPResourceAgent: A connected MCPResourceAgent instance.
        """
        agent = cls(
            system_message=system_message,
            model=model,
            mcp_toolkit=mcp_toolkit,
            mcp_config=mcp_config,
            **kwargs,
        )

        # Connect to MCP servers if toolkit is available
        if agent.mcp_toolkit:
            await agent.mcp_toolkit.connect()

        # Do initial resource loading
        await agent._refresh_system_message()

        return agent

    def _extract_resource_urls(self, text: str) -> List[str]:
        r"""Extract MCP resource URLs from the text.

        Args:
            text: Text containing resource URLs wrapped in XML tags.

        Returns:
            List of resource URLs found in the text.
        """
        # Pattern to match <resource>coral://...</resource>
        pattern = r'<resource>(coral://[^<]+)</resource>'
        return re.findall(pattern, text)

    async def _fetch_resource(self, url: str) -> str:
        r"""Fetch content from an MCP resource URL.

        Args:
            url: MCP resource URL (e.g., coral://server/path).

        Returns:
            The content of the resource.
        """
        if not self.mcp_toolkit:
            return f"[Error: MCP toolkit not configured to fetch {url}]"

        try:
            # Parse the URL to extract server and path
            # Format: coral://server_name/resource_path
            match = re.match(r'coral://([^/]+)/(.+)', url)
            if not match:
                return f"[Error: Invalid MCP URL format: {url}]"

            server_name, resource_path = match.groups()

            # Find the appropriate client
            client: MCPClient = None
            # Find the self.mcp_toolkit.clients[] with the url matching the resource
            # TODO: More specific server matching logic
            for c in self.mcp_toolkit.clients:
                if c.config.url.startswith(f"http://{server_name}"):
                    client = c
                    break

            if not client:
                return f"[Error: Server '{server_name}' not found in MCP toolkit]"

            session: ClientSession = client.session

            # Read the resource
            result  =await session.read_resource("coral://" + server_name + "/" + resource_path)


            # Extract text content
            if result.contents and len(result.contents) > 0:
                return result.contents[0].text or f"[Error: Empty resource at {url}]"
            else:
                return f"[Error: No content in resource at {url}]"

        except Exception as e:
            return f"[Error fetching {url}: {str(e)}]"

    async def _refresh_system_message(self) -> None:
        r"""Refresh the system message by fetching all MCP resources."""
        if not self._template_system_message:
            return

        # Get the template content
        template_content = (
            self._template_system_message.content
            if isinstance(self._template_system_message, BaseMessage)
            else self._template_system_message
        )

        if not template_content:
            return

        # Extract all resource URLs
        resource_urls = self._extract_resource_urls(template_content)

        if not resource_urls:
            # No resources to fetch
            return

        # Fetch all resources
        updated_content = template_content
        for url in resource_urls:
            # Fetch the resource
            content = await self._fetch_resource(url)
            self.resource_cache[url] = content

            # Replace the resource tag with the actual content
            resource_tag = f"<resource>{url}</resource>"
            replacement = f"<resource_content url=\"{url}\">\n{content}\n</resource_content>"
            updated_content = updated_content.replace(resource_tag, replacement)

        # Create new system message with updated content
        if isinstance(self._template_system_message, BaseMessage):
            new_system_message = self._template_system_message.create_new_instance(
                updated_content
            )
        else:
            new_system_message = BaseMessage.make_assistant_message(
                role_name="Assistant",
                content=updated_content
            )

        # Update the system message in memory
        first_message = self.memory._chat_history_block.storage.memory_list[0]
        if first_message['role_at_backend'].value != 'system':
            raise ValueError(
                "First message in memory is not a system message, cannot update."
            )
        # edit the first message in memory
        first_message['message']['content'] = new_system_message.content


        # Set the new system message
        self._system_message = new_system_message


    async def _aget_model_response(
        self,
        openai_messages: List[OpenAIMessage],
        num_tokens: int,
        response_format: Optional[Type[BaseModel]] = None,
        tool_schemas: Optional[List[Dict[str, Any]]] = None,
    ) -> ModelResponse:
        r"""Internal function for agent step model response. Same as super but without swallowing exceptions(!!!)"""

        response = await self.model_backend_real.arun(
            openai_messages, response_format, tool_schemas or None
        )

        if isinstance(response, ChatCompletion):
            return self._handle_batch_response(response)
        else:
            return await self._ahandle_stream_response(response, num_tokens)


    async def astep_with_system_refresh_after_each_intermediate_response(
            self,
            input_message: Union[BaseMessage, str],
            response_format: Optional[Type[BaseModel]] = None,
    ) -> ChatAgentResponse:
        r"""Performs a single step with dynamic resource loading and refreshes
        the system message after each intermediate response.

        This method is useful when the agent might have quite a few async calls (including waiting for mentions) before
        returning


        Args:
            input_message: The input message to process.
            response_format: Optional response format specification.

        Returns:
            ChatAgentResponse: The agent's response.
        """


        self.model_backend_real.model_config_dict['temperature'] = (
            self.original_temperature
        )
        self.model_backend_real.model_config_dict['frequency_penalty'] = (
            self.original_frequency_penalty
        )
        self.model_backend_real.model_config_dict['top_p'] = self.original_top_p

        # Handle response format compatibility with non-strict tools
        original_response_format = response_format
        input_message, response_format, used_prompt_formatting = (
            self._handle_response_format_with_non_strict_tools(
                input_message, response_format
            )
        )

        if isinstance(input_message, str):
            input_message = BaseMessage.make_user_message(
                role_name="User", content=input_message
            )

        self.update_memory(input_message, OpenAIBackendRole.USER)

        tool_call_records: List[ToolCallingRecord] = []
        external_tool_call_requests: Optional[List[ToolCallRequest]] = None
        accumulated_context_tokens = (
            0  # This tracks cumulative context tokens, not API usage tokens
        )

        # Initialize token usage tracker
        step_token_usage = self._create_token_usage_tracker()
        iteration_count = 0
        while True:
            try:
                openai_messages, num_tokens = self.memory.get_context()
                accumulated_context_tokens += num_tokens
            except RuntimeError as e:
                return self._step_terminate(
                    e.args[1], tool_call_records, "max_tokens_exceeded"
                )


            # Try up to 3 times to get a non-repeating response
            repeating_iteration_count = 0
            response = None
            for _ in range(7):

                self.model_backend_real.model_config_dict['temperature'] = min(
                    self.original_temperature  + (0.2 * repeating_iteration_count),
                    2.0
                )
                self.model_backend_real.model_config_dict['frequency_penalty'] = min(
                    self.original_frequency_penalty  + (0.2 * repeating_iteration_count),
                    2.0
                )
                self.model_backend_real.model_config_dict['top_p'] = min(
                    self.original_top_p  + (0.04 * repeating_iteration_count),
                    1.0
                )
                repeating_iteration_count += 1
                print(f"kwargs are: {self.model_backend_real.model_config_dict}")

                try:
                    response = await self._aget_model_response(
                        openai_messages,
                        accumulated_context_tokens,
                        response_format,
                        self._get_full_tool_schemas(),
                    )

                    print('\033[92m' + str(response.tool_call_requests) + '\033[0m')
                    print('\n' * 2)

                    iteration_count += 1
                except openai.BadRequestError as e:
                    print(f"!!!! BadRequestError: {repr(e)}")
                    print(f"Input messages: {json.dumps(openai_messages, indent=2)}")
                    return self._step_terminate(
                        num_tokens, tool_call_records, "bad_request_error"
                    )

                try:
                    new_most_recent = extract_meaningful_json(
                        response.model_dump_json()
                    )
                    # Number of duplicates in the most recent message jsons
                    num_duplicates = self.most_recent_message_jsons.count(
                        json.dumps(new_most_recent, sort_keys=True)
                    )
                    if num_duplicates > 2:
                        print(f"!!!!! Duplicate response detected, rerunning with higher temperature and frequency penalty. Try {repeating_iteration_count} of 6")
                    else:
                        if repeating_iteration_count > 1:
                            print("We broke out of a loop!")
                            print(f"new_most_recent: {new_most_recent}")
                            print(f"self.most_recent_message_json: {self.most_recent_message_jsons[-1]}")
                        break



                    self.most_recent_message_jsons.append(
                        json.dumps(new_most_recent, sort_keys=True)
                    )
                    print(f"Most recent: {self.most_recent_message_jsons[-1]}")
                except Exception as e:
                    print(f"Error extracting meaningful JSON: {repr(e)}")
                    print(f"Response JSON: {response.model_dump_json()}")


                if self.debug and new_most_recent:
                    print(
                        f"[DeBuG] {new_most_recent}"
                    )


            # Accumulate API token usage
            self._update_token_usage_tracker(
                step_token_usage, response.usage_dict
            )

            # Terminate Agent if stop_event is set
            if self.stop_event and self.stop_event.is_set():
                # Use the _step_terminate to terminate the agent with reason
                return self._step_terminate(
                    accumulated_context_tokens,
                    tool_call_records,
                    "termination_triggered",
                )

            if tool_call_requests := response.tool_call_requests:
                # Process all tool calls
                for tool_call_request in tool_call_requests:
                    if (
                        tool_call_request.tool_name
                        in self._external_tool_schemas
                    ):
                        if external_tool_call_requests is None:
                            external_tool_call_requests = []
                        external_tool_call_requests.append(tool_call_request)
                    else:
                        tool_call_record = await self._aexecute_tool(
                            tool_call_request
                        )
                        tool_call_records.append(tool_call_record)

                        print('\033[34m' + str(tool_call_record) + '\033[0m')
                        print('\n' * 2)

                # If we found an external tool call, break the loop
                if external_tool_call_requests:
                    break

                if (
                    self.max_iteration is not None
                    and iteration_count >= self.max_iteration
                ):
                    break

                # If we're still here, continue the loop
                continue

            break

        await self._aformat_response_if_needed(response, response_format)

        # Apply manual parsing if we used prompt-based formatting
        if used_prompt_formatting and original_response_format:
            self._apply_prompt_based_parsing(
                response, original_response_format
            )

        self._record_final_output(response.output_messages)

        return self._convert_to_chatagent_response(
            response,
            tool_call_records,
            accumulated_context_tokens,
            external_tool_call_requests,
            step_token_usage["prompt_tokens"],
            step_token_usage["completion_tokens"],
            step_token_usage["total_tokens"],
        )

    async def astep(
            self,
            input_message: Union[BaseMessage, str],
            response_format: Optional[Type[BaseModel]] = None,
    ) -> ChatAgentResponse:
        r"""Performs a single step with dynamic resource loading.

        Before processing the input message, this method refreshes all MCP
        resources referenced in the system message to ensure the agent has
        access to the most current information.

        Args:
            input_message: The input message to process.
            response_format: Optional response format specification.

        Returns:
            ChatAgentResponse: The agent's response.
        """
        try:
            # Refresh system message with latest resource content
            await self._refresh_system_message()

            print('\033[95m' + input_message + '\033[0m')
            print('\n' * 2)

            step_result = await self.astep_with_system_refresh_after_each_intermediate_response(
                input_message, response_format
            )

            return step_result
        except Exception as e:
            print(f"Error during MCPResourceAgent step: {repr(e)}")
            import traceback
            traceback.print_exc()
            super.reset()
            raise e

    def step(
            self,
            input_message: Union[BaseMessage, str],
            response_format: Optional[Type[BaseModel]] = None,
    ) -> ChatAgentResponse:
        r"""Synchronous step function that refreshes resources before processing.

        Args:
            input_message: The input message to process.
            response_format: Optional response format specification.

        Returns:
            ChatAgentResponse: The agent's response.
        """
        print(
            "MCPResourceAgent does not support synchronous step execution. "
            "Use astep() for asynchronous execution."
        )
        raise NotImplementedError(
            "MCPResourceAgent does not support synchronous step execution. "
            "Use astep() for asynchronous execution."
        )

    async def disconnect(self) -> None:
        r"""Disconnect from MCP servers."""
        if self.mcp_toolkit:
            await self.mcp_toolkit.disconnect()

    async def __aenter__(self):
        r"""Async context manager entry."""
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        r"""Async context manager exit."""
        await self.disconnect()


def extract_meaningful_json(response_json: str) -> Dict[str, Any]:
    """
    Extract meaningful parts of the response JSON for comparison.

    This function extracts:
    - Tool calls (name and arguments only, no IDs)
    - Message content (if meaningful)
    - Finish reasons

    Args:
        response_json: JSON string of the response

    Returns:
        Dictionary containing only the meaningful parts for comparison
    """
    # Parse the JSON string
    data = json.loads(response_json)

    # Initialize the result
    result = {
        "tool_calls": [],
        "content": None,
        "finish_reasons": []
    }

    # Extract tool calls from the response
    if "response" in data and "choices" in data["response"]:
        for choice in data["response"]["choices"]:
            # Add finish reason
            if "finish_reason" in choice:
                result["finish_reasons"].append(choice["finish_reason"])

            # Extract message content if it's not empty
            if "message" in choice and "content" in choice["message"]:
                content = choice["message"]["content"].strip()
                if content:  # Only include if not empty
                    result["content"] = content

            # Extract tool calls
            if "message" in choice and "tool_calls" in choice["message"] and choice["message"]["tool_calls"] is not None:
                for tool_call in choice["message"]["tool_calls"]:
                    if "function" in tool_call:
                        # Parse the arguments JSON string
                        args = json.loads(tool_call["function"]["arguments"])

                        tool_info = {
                            "name": tool_call["function"]["name"],
                            "arguments": args
                        }
                        result["tool_calls"].append(tool_info)

    # Also check for tool_call_requests in the data (alternative format)
    if "tool_call_requests" in data and isinstance(data["tool_call_requests"], list):
        for tool_request in data["tool_call_requests"]:
            tool_info = {
                "name": tool_request["tool_name"],
                "arguments": tool_request["args"]
            }
            # Avoid duplicates
            if tool_info not in result["tool_calls"]:
                result["tool_calls"].append(tool_info)

    # Clean up the result - remove empty lists and None values
    cleaned_result = {}
    if result["tool_calls"]:
        cleaned_result["tool_calls"] = result["tool_calls"]
    if result["content"]:
        cleaned_result["content"] = result["content"]
    if result["finish_reasons"]:
        # Remove duplicates and keep unique values
        cleaned_result["finish_reasons"] = list(set(result["finish_reasons"]))

    return cleaned_result
