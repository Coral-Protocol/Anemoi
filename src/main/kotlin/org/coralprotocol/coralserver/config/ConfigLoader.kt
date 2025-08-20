package org.coralprotocol.coralserver.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import com.sksamuel.hoplite.ConfigLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.files.FileNotFoundException
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.*
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger {}

/**
 * Creates a flow WatchEvent from a watchService
 */
fun WatchService.eventFlow(): Flow<List<WatchEvent<out Any>>> = flow {
    while (currentCoroutineContext().isActive) {
        coroutineScope {
            var key: WatchKey? = null
            val job = launch {
                runInterruptible(Dispatchers.IO) {
                    key = take()
                }
            }
            job.join()
            val currentKey = key
            if (currentKey != null) {
                emit(currentKey.pollEvents())
                currentKey.reset()
            }
        }
    }
}

/**
 * Returns a flow with the files inside a folder (with a given glob)
 */
fun Path.listDirectoryEntriesFlow(glob: String): Flow<List<Path>> {
    val watchService = watch()
    return watchService.eventFlow()
        .map { listDirectoryEntries(glob) }
        .onStart { emit(listDirectoryEntries(glob)) }
        .onCompletion { watchService.close() }
        .flowOn(Dispatchers.IO)
}

/**
 * Creates a new WatchService for any Event
 */
fun Path.watch(): WatchService {
    return watch(
        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.OVERFLOW, StandardWatchEventKinds.ENTRY_DELETE
    )
}

/**
 * Creates a new watch service
 */
fun Path.watch(vararg events: WatchEvent.Kind<out Any>) =
    fileSystem.newWatchService()!!.apply { register(this, events) }


/**
 * Loads application configuration from resources.
 */
class AppConfigLoader(val path: Path? = getConfigPath(), val defaultConfig: AppConfig = AppConfig(
    applications = listOf(
        ApplicationConfig(
            id = "default-app",
            name = "Default Application",
            description = "Default application (fallback)",
            privacyKeys = listOf("default-key", "public")
        )
    )
)) {
    var config: AppConfig = loadConfig(path)
        private set

    private val watchJob: Job? = path?.let {
        CoroutineScope(Dispatchers.Default).launch {
            logger.info{ "Watching for config changes in '${it.parent}'..." }
            it.parent.listDirectoryEntriesFlow("application.yaml*").distinctUntilChanged().collect {
                logger.info { "application.yaml changed. Reloading..." }
                config = loadConfig(path)
            }
        }
    }

    fun stopWatch() {
        watchJob?.cancel()
    }

    companion object {
        private fun getConfigPath(): Path? {
            val configPath = System.getenv("CONFIG_PATH")
            val resourcePath = "application.yaml"
            // Try to load from resources, if no config path set
            return when (configPath) {
                null -> if(Path.of("./application.yaml").toFile().exists()) {
                    Path.of("./application.yaml") // Check local application.yaml
                } else Path.of("./src/main/resources/application.yaml") // Assume running from source when config path not specified

                else -> (Path.of(configPath, resourcePath))
            }
        }
    }

    /**
     * Loads the application configuration from the resources.
     * If the configuration is already loaded, returns the cached instance.
     */
    private fun loadConfig(path: Path?): AppConfig = try {
        val file = path?.toFile()
        if (file != null) {
            if (!file.exists()) {
                throw FileNotFoundException(file.absolutePath)
            }

            val c =
                Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property)).decodeFromStream<AppConfig>(
                    file.inputStream()
                )
            config = c

            logger.info { "Loaded configuration with ${c.applications.size ?: 0} applications & ${c.registry?.agents?.size ?: 0} registry agents" }
            c
        } else {
            throw Exception("Failed to lookup resource.")
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to load configuration, using default" }
        defaultConfig
    }


    /**
     * Validates if the application ID and privacy key are valid.
     */
    fun isValidApplication(applicationId: String, privacyKey: String): Boolean {
        val application = config.applications.find { it.id == applicationId }
        return application != null && application.privacyKeys.contains(privacyKey)
    }

    /**
     * Gets an application by ID.
     */
    fun getApplication(applicationId: String): ApplicationConfig? {
        return config.applications.find { it.id == applicationId }
    }
}

fun AppConfigLoader.Companion.custom(config: AppConfig) = AppConfigLoader(path = null, defaultConfig = config)
