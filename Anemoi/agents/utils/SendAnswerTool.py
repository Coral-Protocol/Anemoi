import logging
import os
from typing import List, Optional

from camel.toolkits import BaseToolkit, FunctionTool

logger = logging.getLogger(__name__)


# get the sesssion id from the connection URL (the path param after /public/ and before /sse)
session_id = os.getenv("CORAL_SESSION_ID")


class SendAnswerToolkit(BaseToolkit):
    r"""A toolkit that sends answers to localhost:12081/answers."""

    def __init__(self):
        super().__init__()
        self.server_url = "http://localhost:12081/answers"
        self.stored_justifications: List[str] = []
        self.stored_evidence: List[dict] = []

    def submit_evidence(self, evidence_type: str, content: str, source: Optional[str] = None) -> str:
        r"""
        Submits a piece of evidence, quote, source, calculation, or other supporting material.
        Call this once per piece of evidence to build up a collection of supporting materials.

        :param evidence_type: Type of evidence (e.g., "quote", "calculation", "source", "excerpt", "data", "reference")
        :param content: The actual evidence content (quote text, calculation steps, data values, etc.)
        :param source: Optional source attribution (e.g., "Agent Smith", "Wikipedia", "calculation", "team discussion")
        :return: Confirmation message
        """
        evidence_entry = {
            "type": evidence_type,
            "content": content,
            "source": source
        }
        self.stored_evidence.append(evidence_entry)

        evidence_count = len(self.stored_evidence)
        logger.info(f"Evidence #{evidence_count} stored successfully: {evidence_type}")
        return f"Evidence #{evidence_count} ({evidence_type}) submitted successfully. Total evidence pieces: {evidence_count}"

    def submit_justification(self, justification: str) -> str:
        r"""
        Submits a justification for the answer. Can be called multiple times to build up the complete justification.
        Multiple justifications will be combined together when sending the final answer.
        Requires at least one piece of evidence to be submitted first using submit_evidence().

        :param justification: A step by step explanation of how you and the team arrived at the answer. Make sure to interview with the team to figure this out. Write down certainty % for each step / axiom / assumption. Also include verbatim quotes from the team. You can work out this justification separately first and check it makes sense before choosing to send the answer or work on it further. Every bit of information should be sourced to where it came from. Also remember to escape newlines and other special characters in the justification string.
        :return: Confirmation message
        """
        if not self.stored_evidence:
            raise ValueError(
                "You must submit at least one piece of evidence using submit_evidence() before submitting a justification.")

        self.stored_justifications.append(justification)

        justification_count = len(self.stored_justifications)
        logger.info(f"Justification part #{justification_count} stored successfully")
        return f"Justification part #{justification_count} submitted successfully. Total justification parts: {justification_count}"

    def _format_evidence(self) -> str:
        r"""Formats the stored evidence into a string for inclusion in the answer submission."""
        if not self.stored_evidence:
            return ""

        formatted_evidence = "\n\n=== EVIDENCE AND SOURCES ===\n"
        for i, evidence in enumerate(self.stored_evidence, 1):
            formatted_evidence += f"\n[{i}] {evidence['type'].upper()}"
            if evidence['source']:
                formatted_evidence += f" (Source: {evidence['source']})"
            formatted_evidence += f":\n{evidence['content']}\n"

        return formatted_evidence

    def _combine_justifications(self) -> str:
        r"""Combines all stored justifications into a single string."""
        if not self.stored_justifications:
            return ""

        # If there's only one justification, return it as-is
        if len(self.stored_justifications) == 1:
            return self.stored_justifications[0]

        # Otherwise, combine them with clear separation
        combined = "=== JUSTIFICATION ===\n"
        for i, justification in enumerate(self.stored_justifications, 1):
            combined += f"\nPart {i}:\n{justification}\n"

        return combined

    def send_answer(self, answer: str, certainty_percentage: int) -> None:
        r"""
        Sends the final answer to the server. Requires at least one justification to be submitted first.
        Will include all submitted evidence and justifications.

        :param answer: The final answer that you are certain is correct.
        :param certainty_percentage: The overall certainty percentage of the answer (0-100).
        :return:
        """
        if not self.stored_justifications:
            raise ValueError(
                "You must submit at least one justification using submit_justification() before sending an answer.")

        import requests
        try:
            # Combine justifications and append evidence
            full_justification = self._combine_justifications()
            if self.stored_evidence:
                full_justification += self._format_evidence()

            answer_data = {
                "answer": answer,
                "questionId": os.getenv("TASK_ID", "No task id specified!"),
                "justification": full_justification,
                "certaintyPercentage": certainty_percentage,
                "sessionId": session_id
            }
            response = requests.post(self.server_url, json=answer_data, headers={"Content-Type": "application/json"})
            response.raise_for_status()
            logger.info(f"Answer sent successfully: {answer}")

            # Clear all stored data after successful submission
            self.stored_justifications.clear()
            self.stored_evidence.clear()
        except requests.RequestException as e:
            logger.error(f"Failed to send answer: {repr(e)}")
            raise

    def give_up(self, reason: str) -> None:
        r"""
        Sends a message to the server indicating that the agent is giving up on the task.
        Requires at least one justification to be submitted first.
        Will include all submitted evidence and justifications.

        :param reason: The reason for giving up on the task.
        """
        if not self.stored_justifications:
            raise ValueError(
                "You must submit at least one justification using submit_justification() before giving up.")

        import requests
        try:
            # Combine justifications and append evidence
            full_justification = self._combine_justifications()
            if self.stored_evidence:
                full_justification += self._format_evidence()

            answer_data = {
                "answer": "give up: " + reason,
                "questionId": os.getenv("TASK_ID", "No task id specified!"),
                "sessionId": session_id,
                "justification": full_justification
            }
            response = requests.post(self.server_url, json=answer_data, headers={"Content-Type": "application/json"})
            response.raise_for_status()
            logger.info("Give up message sent successfully.")

            # Clear all stored data after successful submission
            self.stored_justifications.clear()
            self.stored_evidence.clear()
        except requests.RequestException as e:
            logger.error(f"Failed to send give up message: {repr(e)}")
            raise

    def get_tools(self) -> List[FunctionTool]:
        r"""Returns a list of FunctionTool objects representing the
        functions in the toolkit.

        Returns:
            List[FunctionTool]: A list of FunctionTool objects
                representing the functions in the toolkit.
        """
        return [
            FunctionTool(self.submit_evidence),
            FunctionTool(self.submit_justification),
            FunctionTool(self.send_answer),
            FunctionTool(self.give_up)
        ]