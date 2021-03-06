package io.sweers.palettehelper

import android.app.Application
import com.bugsnag.android.Bugsnag
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.squareup.leakcanary.LeakCanary
import timber.log.Timber
import kotlin.properties.Delegates

class PaletteHelperApplication: Application() {

    companion object {
        var mixPanel: MixpanelAPI by Delegates.notNull()
    }

    override fun onCreate() {
        super.onCreate()

        LeakCanary.install(this);

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Bugsnag.init(this, BuildConfig.BUGSNAG_KEY)
            Bugsnag.setReleaseStage(BuildConfig.BUILD_TYPE)
            Bugsnag.setProjectPackages("io.sweers.palettehelper")

            val tree = BugsnagTree()
            Bugsnag.getClient().beforeNotify({ error ->
                tree.update(error)
                true
            })

            Timber.plant(tree)
        }

        setUpAnalytics()
    }

    public fun setUpAnalytics() {
        mixPanel = MixpanelAPI.getInstance(this, BuildConfig.ANALYTICS_KEY)
    }
}
