package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import com.chrynan.uri.core.parse
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import org.coralprotocol.coralserver.orchestrator.RuntimeEvent
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("docker")
data class Docker(
    val image: String,
    val environment: List<EnvVar> = listOf()
) : AgentRuntime() {
    private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(getDockerSocket())
        .build()
    private val dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build()

    override fun spawn(
        params: RuntimeParams,
        bus: EventBus<RuntimeEvent>,
    ): OrchestratorHandle {
        logger.info { "Spawning Docker container with image: $image" }
        val fullConnectionUrl =
            "http://host.docker.internal:${params.mcpServerPort}/${params.mcpServerRelativeUri.path}${params.mcpServerRelativeUri.query?.let { "?$it" } ?: ""}"

        val resolvedEnvs = this.environment.map {
            val (key, value) = it.resolve(params.options)
            "$key=$value"
        }
        val allEnvs = resolvedEnvs + getCoralSystemEnvs(
            params,
            Uri.parse(fullConnectionUrl),
            "docker"
        ).map { (key, value) -> "$key=$value" }

        val containerCreation = dockerClient.createContainerCmd(image)
            .withName(getDockerContainerName(params.mcpServerRelativeUri, params.agentName))
            .withEnv(allEnvs)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withAttachStdin(false) // Stdin makes no sense with orchestration
            .exec()

        dockerClient.startContainerCmd(containerCreation.id).exec()

        // Attach to container streams for output redirection
        val attachCmd = dockerClient.attachContainerCmd(containerCreation.id)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .withLogs(true)

        val streamCallback = attachCmd.exec(object : ResultCallback.Adapter<Frame>() {
            override fun onNext(frame: Frame) {
                val message = String(frame.payload).trimEnd('\n')
                when (frame.streamType) {
                    StreamType.STDOUT -> {
                        logger.info { "[STDOUT] ${params.agentName}: $message" }
                    }

                    StreamType.STDERR -> {
                        logger.info { "[STDERR] ${params.agentName}: $message" }
                    }

                    else -> {
                        logger.warn { "[UNKNOWN] ${params.agentName}: $message" }
                    }
                }
            }
        })

        return object : OrchestratorHandle {
            override suspend fun destroy() {
                withContext(processContext) {
                    try {
                        streamCallback.close()
                    } catch (e: Exception) {
                        logger.warn { "Failed to close stream callback: ${e.message}" }
                    }

                    warnOnNotModifiedExceptions { dockerClient.stopContainerCmd(containerCreation.id).exec() }
                    warnOnNotModifiedExceptions {
                        withTimeoutOrNull(30.seconds) {
                            dockerClient.removeContainerCmd(containerCreation.id)
                                .withRemoveVolumes(true)
                                .exec()
                            return@withTimeoutOrNull true
                        } ?: let {
                            logger.warn { "Docker container ${params.agentName} did not stop in time, force removing it" }
                            dockerClient.removeContainerCmd(containerCreation.id)
                                .withRemoveVolumes(true)
                                .withForce(true)
                                .exec()
                        }
                        logger.info { "Docker container ${params.agentName} stopped and removed" }
                    }
                }
            }

            override var sessionId: String = params.sessionId
        }
    }
}

private suspend fun warnOnNotModifiedExceptions(block: suspend () -> Unit): Unit {
    try {
        block()
    } catch (e: NotModifiedException) {
        logger.warn { "Docker operation was not modified: ${e.message}" }
    } catch (e: Exception) {
        throw e
    }
}

private fun String.asDockerContainerName(): String {
    return this.replace(Regex("[^a-zA-Z0-9_]"), "_")
        .take(63) // Network-resolvable name limit
        .trim('_')
}

private fun getDockerContainerName(relativeMcpServerUri: Uri, agentName: String): String {
    // SessionID is too long for Docker container names, so we use a hash of the URI for deduplication.
    val randomSuffix = relativeMcpServerUri.toUriString().hashCode().toString(16).take(11)
    return "${agentName.take(52)}_$randomSuffix".asDockerContainerName()
}

private fun getDockerSocket(): String {
    val specifiedSocket = System.getenv("CORAL_DOCKER_SOCKET")
    if (specifiedSocket != null) {
        return specifiedSocket
    }

    // Check whether colima is installed and use its socket if available
    val homeDir = System.getProperty("user.home")
    val colimaSocket = "$homeDir/.colima/default/docker.sock"
    return if (java.io.File(colimaSocket).exists()) {
        "unix://$colimaSocket"
    } else {
        "unix:///var/run/docker.sock" // Default Docker socket
    }
}