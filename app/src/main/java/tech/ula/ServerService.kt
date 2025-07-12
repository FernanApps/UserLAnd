package tech.ula

import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.model.entities.App
import tech.ula.model.entities.ServiceType
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Session
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import kotlin.coroutines.CoroutineContext

class ServerService : Service(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    companion object {
        const val SERVER_SERVICE_RESULT: String = "tech.ula.ServerService.RESULT"
    }

    private val activeSessions: MutableMap<Long, Session> = mutableMapOf()

    private lateinit var broadcaster: LocalBroadcastManager

    private val notificationManager: NotificationConstructor by lazy {
        NotificationConstructor(this)
    }

    private val busyboxExecutor by lazy {
        val ulaFiles = UlaFiles(this, this.applicationInfo.nativeLibraryDir)
        val prootDebugLogger = ProotDebugLogger(this.defaultSharedPreferences, ulaFiles)
        BusyboxExecutor(ulaFiles, prootDebugLogger)
    }

    private val localServerManager by lazy {
        LocalServerManager(this.filesDir.path, busyboxExecutor)
    }

    override fun onCreate() {
        broadcaster = LocalBroadcastManager.getInstance(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /*override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.getStringExtra("type")) {
            "start" -> {
                val session: Session = intent.getParcelableExtra("session")!!
                this.launch { startSession(session) }
            }
            "stopApp" -> {
                val app: App = intent.getParcelableExtra("app")!!
                stopApp(app)
            }
            "restartRunningSession" -> {
                val session: Session = intent.getParcelableExtra("session")!!
                startClient(session)
            }
            "kill" -> {
                val session: Session = intent.getParcelableExtra("session")!!
                killSession(session)
            }
            "filesystemIsBeingDeleted" -> {
                val filesystemId: Long = intent.getLongExtra("filesystemId", -1)
                cleanUpFilesystem(filesystemId)
            }
            "stopAll" -> {
                activeSessions.forEach { (_, session) ->
                    killSession(session)
                }
            }
        }

        return START_STICKY
    }*/

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i("TESTING", "onStartCommand called")

        when (intent?.getStringExtra("type")) {
            "start" -> {
                Log.i("TESTING", "Action 'start' received")
                val session: Session = intent.getParcelableExtra("session")!!
                this.launch { startSession(session) }
            }
            "stopApp" -> {
                Log.i("TESTING", "Action 'stopApp' received")
                val app: App = intent.getParcelableExtra("app")!!
                stopApp(app)
            }
            "restartRunningSession" -> {
                Log.i("TESTING", "Action 'restartRunningSession' received")
                val session: Session = intent.getParcelableExtra("session")!!
                startClient(session)
            }
            "kill" -> {
                Log.i("TESTING", "Action 'kill' received")
                val session: Session = intent.getParcelableExtra("session")!!
                killSession(session)
            }
            "filesystemIsBeingDeleted" -> {
                Log.i("TESTING", "Action 'filesystemIsBeingDeleted' received")
                val filesystemId: Long = intent.getLongExtra("filesystemId", -1)
                cleanUpFilesystem(filesystemId)
            }
            "stopAll" -> {
                Log.i("TESTING", "Action 'stopAll' received")
                activeSessions.forEach { (_, session) ->
                    killSession(session)
                }
            }
            else -> {
                Log.i("TESTING", "Unknown action received: ${intent?.getStringExtra("type")}")
            }
        }

        return START_STICKY
    }
    // Used in conjunction with manifest attribute `android:stopWithTask="true"`
    // to clean up when app is swiped away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Redundancy to ensure no hanging processes, given broad device spectrum.
        this.coroutineContext.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Redundancy to ensure no hanging processes, given broad device spectrum.
        this.coroutineContext.cancel()
    }

    private fun removeSession(session: Session) {
        activeSessions.remove(session.pid)
        if (activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateSession(session: Session) = CoroutineScope(Dispatchers.Default).launch {
        UlaDatabase.getInstance(this@ServerService).sessionDao().updateSession(session)
    }

    private fun killSession(session: Session) {
        localServerManager.stopService(session)
        removeSession(session)
        session.active = false
        updateSession(session)
    }

    private suspend fun startSession(session: Session) {
        Log.i("TESTING", "startSession called for session: ${session.name}")

        startForeground(NotificationConstructor.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
        Log.i("TESTING", "Foreground notification started")

        session.pid = localServerManager.startServer(session)
        Log.i("TESTING", "Server started with PID: ${session.pid}")

        while (!localServerManager.isServerRunning(session)) {
            Log.i("TESTING", "Waiting for server to start...")
            delay(500)
        }

        Log.i("TESTING", "Server is now running")

        session.active = true
        updateSession(session)
        Log.i("TESTING", "Session marked as active and updated in DB")

        //startClient(session)
        //Log.i("TESTING", "startClient called")

        activeSessions[session.pid] = session
        Log.i("TESTING", "Session added to active sessions map")
    }

    private fun stopApp(app: App) {
        val appSessions = activeSessions.filter { (_, session) ->
            session.name == app.name
        }
        appSessions.forEach { (_, session) ->
            killSession(session)
        }
    }

    private fun startClient(session: Session) {
        Log.i("TESTING", "startClient called for service type: ${session.serviceType}")

        when (session.serviceType) {
            ServiceType.Ssh -> {
                Log.i("TESTING", "Starting SSH client")
                startSshClient(session)
            }
            ServiceType.Vnc -> {
                Log.i("TESTING", "Starting VNC client")
                startVncClient(session, "com.iiordanov.freebVNC")
            }
            ServiceType.Xsdl -> {
                Log.i("TESTING", "Starting XSDL client")
                startXsdlClient("x.org.server")
            }
            else -> {
                Log.i("TESTING", "Unhandled service type: ${session.serviceType}")
                sendDialogBroadcast("unhandledSessionServiceType")
            }
        }
        sendSessionActivatedBroadcast()
        Log.i("TESTING", "Session activated broadcast sent")
    }

    private fun startSshClient(session: Session) {
        Log.i("TESTING", "startSshClient: Preparing ConnectBot intent")

        val connectBotIntent = Intent()
        connectBotIntent.action = Intent.ACTION_VIEW
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:2022/#userland")
        connectBotIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        Log.i("TESTING", "startSshClient: Starting activity with URI: ${connectBotIntent.data}")
        startActivity(connectBotIntent)
    }

    private fun startVncClient(session: Session, packageName: String) {
        Log.i("TESTING", "startVncClient: Preparing bVNC intent")

        val bVncIntent = Intent()
        bVncIntent.action = Intent.ACTION_VIEW
        bVncIntent.type = "application/vnd.vnc"
        bVncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncUsername=${session.username}&VncPassword=${session.vncPassword}")
        bVncIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (clientIsPresent(bVncIntent)) {
            Log.i("TESTING", "startVncClient: Client found, starting bVNC activity")
            this.startActivity(bVncIntent)
        } else {
            Log.i("TESTING", "startVncClient: Client not found, redirecting to store")
            getClient(packageName)
        }
    }

    private fun startXsdlClient(packageName: String) {
        Log.i("TESTING", "startXsdlClient: Preparing XSDL intent")

        val xsdlIntent = Intent()
        xsdlIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        xsdlIntent.data = Uri.parse("x11://give.me.display:4721")

        if (clientIsPresent(xsdlIntent)) {
            Log.i("TESTING", "startXsdlClient: Client found, starting XSDL activity")
            startActivity(xsdlIntent)
        } else {
            Log.i("TESTING", "startXsdlClient: Client not found, redirecting to store")
            getClient(packageName)
        }
    }

    private fun clientIsPresent(intent: Intent): Boolean {
        val activities = packageManager.queryIntentActivities(intent, 0)
        return (activities.size > 0)
    }

    private fun getClient(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            this.startActivity(intent)
        } catch (err: ActivityNotFoundException) {
            sendDialogBroadcast("playStoreMissingForClient")
        }
    }

    private fun cleanUpFilesystem(filesystemId: Long) {
        activeSessions.values.filter { it.filesystemId == filesystemId }
                .forEach { killSession(it) }
    }

    private fun sendSessionActivatedBroadcast() {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "sessionActivated")
        broadcaster.sendBroadcast(intent)
    }

    private fun sendDialogBroadcast(type: String) {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "dialog")
                .putExtra("dialogType", type)
        broadcaster.sendBroadcast(intent)
    }
}