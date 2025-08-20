root=$(dirname "$(realpath "$0")")
source "${root}/venv/bin/activate"
echo "Using CD: ${AGENT_WORKING_DIRECTORY}"
export PATH="/usr/bin:/opt/homebrew/bin:${PATH}"
cd "${AGENT_WORKING_DIRECTORY}" && python -u "$1"
