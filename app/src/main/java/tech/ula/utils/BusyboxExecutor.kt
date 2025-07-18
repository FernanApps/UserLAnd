package tech.ula.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

sealed class ExecutionResult
data class MissingExecutionAsset(val asset: String) : ExecutionResult()
object SuccessfulExecution : ExecutionResult()
data class FailedExecution(val reason: String) : ExecutionResult()
data class OngoingExecution(val process: Process) : ExecutionResult()

class BusyboxExecutor(
    private val ulaFiles: UlaFiles,
    private val prootDebugLogger: ProotDebugLogger,
    private val busyboxWrapper: BusyboxWrapper = BusyboxWrapper(ulaFiles)
) {

    private val discardOutput: (String) -> Any = { Log.d("busybox", it) }

    fun executeScript(
        scriptCall: String,
        listener: (String) -> Any = discardOutput
    ): ExecutionResult {
        val updatedCommand = busyboxWrapper.wrapScript(scriptCall)

        return runCommand(updatedCommand, listener)
    }

    fun executeCommand(
        command: String,
        listener: (String) -> Any = discardOutput
    ): ExecutionResult {
        val updatedCommand = busyboxWrapper.wrapCommand(command)

        return runCommand(updatedCommand, listener)
    }

    private fun runCommand(command: List<String>, listener: (String) -> Any): ExecutionResult {
        if (!busyboxWrapper.busyboxIsPresent()) {
            return MissingExecutionAsset("busybox")
        }

        val env = busyboxWrapper.getBusyboxEnv()
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(ulaFiles.filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()
            collectOutput(process.inputStream, listener)
            getProcessResult(process)
        } catch (err: Exception) {
            FailedExecution("$err")
        }
    }

    fun executeProotCommandOld(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): ExecutionResult {
        when {
            !busyboxWrapper.busyboxIsPresent() ->
                return MissingExecutionAsset("busybox")
            !busyboxWrapper.prootIsPresent() ->
                return MissingExecutionAsset("proot")
            !busyboxWrapper.executionScriptIsPresent() ->
                return MissingExecutionAsset("execution script")
        }

        val prootDebugEnabled = prootDebugLogger.isEnabled
        val prootDebugLevel =
                if (prootDebugEnabled) prootDebugLogger.verbosityLevel else "-1"

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        val filesystemDir = File("${ulaFiles.filesDir.absolutePath}/$filesystemDirName")

        env.putAll(busyboxWrapper.getProotEnv(filesystemDir, prootDebugLevel))

        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(ulaFiles.filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()
            when {
                prootDebugEnabled && commandShouldTerminate -> {
                    // Call the listener explicitly since all output will be captured by the log
                    listener("Output redirecting to proot debug log")
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    getProcessResult(process)
                }
                prootDebugEnabled && !commandShouldTerminate -> {
                    // Call the listener explicitly since all output will be captured by the log
                    listener("Output redirecting to proot debug log")
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    OngoingExecution(process)
                }
                commandShouldTerminate -> {
                    collectOutput(process.inputStream, listener)
                    getProcessResult(process)
                }
                else -> {
                    OngoingExecution(process)
                }
            }
        } catch (err: Exception) {
            FailedExecution("$err")
        }
    }

    fun executeProotCommand(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): ExecutionResult {
        Log.d("TESTING", "Inicio executeProotCommand")
        Log.d("TESTING", "command: $command")
        Log.d("TESTING", "filesystemDirName: $filesystemDirName")
        Log.d("TESTING", "commandShouldTerminate: $commandShouldTerminate")

        if (!busyboxWrapper.busyboxIsPresent()) {
            Log.d("TESTING", "busybox no está presente")
            return MissingExecutionAsset("busybox")
        }

        if (!busyboxWrapper.prootIsPresent()) {
            Log.d("TESTING", "proot no está presente")
            return MissingExecutionAsset("proot")
        }

        if (!busyboxWrapper.executionScriptIsPresent()) {
            Log.d("TESTING", "execution script no está presente")
            return MissingExecutionAsset("execution script")
        }

        val prootDebugEnabled = prootDebugLogger.isEnabled
        Log.d("TESTING", "prootDebugEnabled: $prootDebugEnabled")

        val prootDebugLevel =
            if (prootDebugEnabled) prootDebugLogger.verbosityLevel else "-1"
        Log.d("TESTING", "prootDebugLevel: $prootDebugLevel")

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        Log.d("TESTING", "updatedCommand: $updatedCommand")

        val filesystemDir = File("${ulaFiles.filesDir.absolutePath}/$filesystemDirName")
        Log.d("TESTING", "filesystemDir: ${filesystemDir.absolutePath}")

        env.putAll(busyboxWrapper.getProotEnv(filesystemDir, prootDebugLevel))
        Log.d("TESTING", "env: $env")

        val processBuilder = ProcessBuilder(updatedCommand)
        Log.d("TESTING", "Creado ProcessBuilder con directorio ${ulaFiles.filesDir}")

        processBuilder.directory(ulaFiles.filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()
            Log.d("TESTING", "Proceso iniciado correctamente")

            when {
                prootDebugEnabled && commandShouldTerminate -> {
                    Log.d("TESTING", "prootDebugEnabled && commandShouldTerminate")
                    listener("Output redirecting to proot debug log")
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    val result = getProcessResult(process)
                    Log.d("TESTING", "Resultado comando terminado: $result")
                    result
                }

                prootDebugEnabled && !commandShouldTerminate -> {
                    Log.d("TESTING", "prootDebugEnabled && !commandShouldTerminate")
                    listener("Output redirecting to proot debug log")
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    val ongoing = OngoingExecution(process)
                    Log.d("TESTING", "Proceso ongoing (debug): $ongoing")
                    ongoing
                }

                commandShouldTerminate -> {
                    Log.d("TESTING", "commandShouldTerminate (sin debug)")
                    collectOutput(process.inputStream, listener)
                    val result = getProcessResult(process)
                    Log.d("TESTING", "Resultado comando terminado: $result")
                    result
                }

                else -> {
                    Log.d("TESTING", "else -> ongoing (sin debug)")
                    val ongoing = OngoingExecution(process)
                    Log.d("TESTING", "Proceso ongoing: $ongoing")
                    ongoing
                }
            }
        } catch (err: Exception) {
            Log.e("TESTING", "Excepción al ejecutar proceso: $err", err)
            FailedExecution("$err")
        }
    }


    suspend fun recursivelyDelete(absolutePath: String): ExecutionResult = withContext(Dispatchers.IO) {
        val command = "rm -rf $absolutePath"
        return@withContext executeCommand(command)
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        val buf = inputStream.bufferedReader(Charsets.UTF_8)

        buf.forEachLine { listener(it) }

        buf.close()
    }

    private fun getProcessResult(process: Process): ExecutionResult {
        return if (process.waitFor() == 0) SuccessfulExecution
        else FailedExecution("Command failed with: ${process.exitValue()}")
    }
}

// This class is intended to allow stubbing of elements that are unavailable during unit tests.
class BusyboxWrapper(private val ulaFiles: UlaFiles) {
    // For basic commands, CWD should be `applicationFilesDir`
    fun wrapCommand(command: String): List<String> {
        return listOf(ulaFiles.busybox.path, "sh", "-c", command)
    }

    fun wrapScript(command: String): List<String> {
        return listOf(ulaFiles.busybox.path, "sh") + command.split(" ")
    }

    fun getBusyboxEnv(): HashMap<String, String> {
        return hashMapOf(
                "LIB_PATH" to ulaFiles.supportDir.absolutePath,
                "ROOT_PATH" to ulaFiles.filesDir.absolutePath
        )
    }

    fun busyboxIsPresent(): Boolean {
        return ulaFiles.busybox.exists()
    }

    // Proot scripts expect CWD to be `applicationFilesDir/<filesystem`
    fun addBusyboxAndProot(command: String): List<String> {
        return listOf(ulaFiles.busybox.absolutePath, "sh", "support/execInProot.sh") + command.split(" ")
    }

    fun getProotEnv(filesystemDir: File, prootDebugLevel: String): HashMap<String, String> {
        // TODO This hack should be removed once there are no users on releases 2.5.14 - 2.6.1
        handleHangingBindingDirectories(filesystemDir)
        val emulatedStorageBinding = "-b ${ulaFiles.emulatedUserDir.absolutePath}:/storage/internal"
        val externalStorageBinding = ulaFiles.sdCardUserDir?.run {
            "-b ${this.absolutePath}:/storage/sdcard"
        } ?: ""
        val bindings = "$emulatedStorageBinding $externalStorageBinding"
        return hashMapOf(
                "LD_LIBRARY_PATH" to ulaFiles.supportDir.absolutePath,
                "LIB_PATH" to ulaFiles.supportDir.absolutePath,
                "ROOT_PATH" to ulaFiles.filesDir.absolutePath,
                "ROOTFS_PATH" to filesystemDir.absolutePath,
                "PROOT_DEBUG_LEVEL" to prootDebugLevel,
                "EXTRA_BINDINGS" to bindings,
                "OS_VERSION" to System.getProperty("os.version")!!
        )
    }

    fun prootIsPresent(): Boolean {
        return ulaFiles.proot.exists()
    }

    fun executionScriptIsPresent(): Boolean {
        val execInProotFile = File(ulaFiles.supportDir, "execInProot.sh")
        return execInProotFile.exists()
    }

    // TODO this hack should be removed when no users are left using version 2.5.14 - 2.6.1
    private fun handleHangingBindingDirectories(filesystemDir: File) {
        // If users upgraded from a version 2.5.14 - 2.6.1, the storage directory will exist but
        // with unusable permissions. It needs to be recreated.
        val storageBindingDir = File(filesystemDir, "storage")
        val storageBindingDirEmpty = storageBindingDir.listFiles()?.isEmpty() ?: true
        if (storageBindingDir.exists() && storageBindingDir.isDirectory && storageBindingDirEmpty) {
            storageBindingDir.delete()
        }
        storageBindingDir.mkdirs()

        // If users upgraded from a version before 2.5.14, the old sdcard binding should be removed
        // to increase clarity.
        val sdCardBindingDir = File(filesystemDir, "sdcard")
        val sdCardBindingDirEmpty = sdCardBindingDir.listFiles()?.isEmpty() ?: true
        if (sdCardBindingDir.exists() && sdCardBindingDir.isDirectory && sdCardBindingDirEmpty) {
            sdCardBindingDir.delete()
        }
    }
}