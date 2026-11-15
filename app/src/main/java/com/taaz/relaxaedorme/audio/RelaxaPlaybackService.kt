package com.taaz.relaxaedorme.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.taaz.relaxaedorme.MainActivity
import com.taaz.relaxaedorme.R

/**
 * Foreground Service oficial de reprodução de mídia em segundo plano do Relaxa e Dorme.
 *
 * Mantém a reprodução de Músicas e do Mixer de Sons Ambientes (incluindo temporizadores de
 * intervalos aleatórios) ativa quando o usuário bloqueia a tela, minimiza o aplicativo ou
 * alterna para outros aplicativos, além de expor o [MediaSession] para controles na tela de
 * bloqueio e na notificação do sistema Android.
 */
class RelaxaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var coordinator: AppAudioSessionCoordinator

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        coordinator = AppAudioSessionCoordinator.getInstance(applicationContext)
        coordinator.onServiceCreated()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(applicationContext)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_bedtime_rounded)
            }
        setMediaNotificationProvider(notificationProvider)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val session = MediaSession.Builder(this, coordinator.sessionPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
        mediaSession = session
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!coordinator.isAnyAudioPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        if (::coordinator.isInitialized) {
            coordinator.onServiceDestroyed()
        }
        super.onDestroy()
    }
}
