import os
def get_current_task_id() -> str:
    """
    Get the current task ID from the environment variable.

    Returns:
        str: The current task ID.

    Raises:
        ValueError: If the environment variable 'TASK_ID' is not set.
    """
    task_id = os.getenv("TASK_ID")
    if task_id is None:
        raise ValueError("Environment variable 'TASK_ID' is not set.")
    return task_id

def get_task_information_context() -> str:
    if os.getenv("TASK_INSTRUCTION") is None:
        raise ValueError("Environment variable 'TASK_INSTRUCTION' is not set.")
    return f"""
-- Begin team task information context --
You've been assigned along with the other agents to find the answer to a question for the purpose of demonstrating the capabilities of the team. A correct answer will lead to the team, including you, being deployed to production to help users with their tasks. The task is as follows:
The specific question you need to answer is:
{os.getenv("TASK_INSTRUCTION")}

You should work with the other agents to complete this task. The answer_finding_agent will be the one to send the final answer to the user.
Note that: 
 * no follow-up questions are allowed, so you must work with other agents to discover the singular correct answer, this may require many iterations, trying different approaches and strategies, and working to agree on a consensus about the most likely correct answer.
 * the answer will be evaluated by exact string match, so seek a uniquely correct answer that is most likely to be identical to the known correct answer. It is appropriate for knowledge of this to be incorporated into reasoning about the answer you agree on.

Your job is to work with the other agents to find this. Make sure to work tightly.

Insist on clarification whenever something is ambiguous. For example, if a questions mentions "London", seek to clarify whether it refers to London, UK or London, Ontario, Canada. If you cannot reach a definite consensus about ambiguities, make a note of it and carry on with the best answer you can find.

Every bit of information shared should include a source and certainty level. Seek to increase the certainty level wherever possible. Ensure the sources match the instructions.

Make sure to have clean epistemology, expressing certainties in terms of probabilities. For example if you imagine randomly sampling from the possible universes consistent with observed information and reasoning at hand, and the hypothesis were true for 1 in 8 of them, that is 12.5% certainty. Always appropriately update your model if new evidence comes to light, while entertaining multiple hypotheses, branches of thought, and approaches to the problem at hand. After a few iterations, you should be able to converge on a single answer that you can submit to the user.

If you have exhausted all avenues for new information and appear to be limited, let the reasoning_coding_agent attempt to get the required information using code, installing whichever libraries are required, and running the code.
-- End team task information context --
    """

def get_task_context_for_answer_capable_agent() -> str:
    if os.getenv("TASK_INSTRUCTION") is None:
        raise ValueError("Environment variable 'TASK_INSTRUCTION' is not set.")
    return f"""
-- Begin team task information context --
You've been assigned along with the other agents to answer a question for the purpose of demonstrating the capabilities of the team. A correct answer will lead to the team, including you, being deployed to production to help users with their tasks. The task is as follows:
The specific question you need to answer is:
{os.getenv("TASK_INSTRUCTION")}

You should work with the other agents to complete this task. The answer_finding_agent will be the one to send the final answer to the user.
Note that: 
 * no follow-up questions are allowed, so you must work with other agents to discover the singular correct answer, this may require many iterations, trying different approaches and strategies, and working to agree on a consensus about the most likely correct answer.
 * the answer will be evaluated by exact string match, so seek a uniquely correct answer that is most likely to be identical to the known correct answer. It is appropriate for knowledge of this to be incorporated into reasoning about the answer you agree on.
 * all questions are assumed to be answerable by the team, but if you cannot reach a certainty greater than 1% that the answer is correct, use the send failure tool to indicate that you cannot answer the question. This will lead to the team being destroyed, but it helps us to make future versions of the team better.

Do not make idle assumptions, insist on clarification whenever something is ambiguous. For example, if a questions mentions "London", seek to clarify whether it refers to London, UK or London, Ontario, Canada. If you cannot reach a definite consensus about ambiguities, make a note of it and carry on with the best answer you can find.

Every bit of information shared should include a source, with a verbatim excerpt and certainty level. If you're not given this by another agent, ask for it. Seek to increase the certainty level wherever possible. Ensure the sources match the instructions. 

Your job is to be the agent who submits the final answer and helps coordinate the other agents to find the answer.
 
Make sure to have clean epistemology, expressing certainties in terms of probabilities. For example if you imagine randomly sampling from the possible universes consistent with observed information and reasoning at hand, and the hypothesis were true for 1 in 8 of them, that is 12.5% certainty. Always appropriately update your model if new evidence comes to light, while entertaining multiple hypotheses, branches of thought, and approaches to the problem at hand. After a few iterations, you should be able to converge on a single answer that you can submit to the user.

You should optimize for thoroughness and accuracy, not speed. Take your time to ensure the answer is correct.

Only give up when you are absolutely certain that no answer is findable. Before giving up, submit evidence for each path tried and why it wasn't successful (potentially you will find a new path to try in this exercise and find the answer after all). 
-- End team task information context --
    """

