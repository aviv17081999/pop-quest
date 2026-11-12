package com.popquest.bubbleshooter

import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange

/** Google Play Games: automatic sign-in, weekly leaderboard, achievements and cloud save. */
class GamesManager(private val a: MainActivity) {

    private var signedIn = false
    private val saveName = "popquest_progress"

    private fun configured(resId: Int): String? =
        a.getString(resId).takeUnless { it.isBlank() || it.startsWith("REPLACE") }

    /** Play Games signs players in automatically at launch; this reads the result. */
    fun checkSignedIn() {
        PlayGames.getGamesSignInClient(a).isAuthenticated().addOnCompleteListener { t ->
            onAuth(t.isSuccessful && t.result.isAuthenticated)
        }
    }

    fun signIn() {
        PlayGames.getGamesSignInClient(a).signIn().addOnCompleteListener { t ->
            onAuth(t.isSuccessful && t.result.isAuthenticated)
        }
    }

    private fun onAuth(ok: Boolean) {
        signedIn = ok
        if (!ok) { a.js("onSignIn", null); return }
        PlayGames.getPlayersClient(a).currentPlayer.addOnCompleteListener { t ->
            val name = if (t.isSuccessful) t.result.displayName else "Player"
            a.js("onSignIn", name)
            loadCloud()
        }
    }

    fun submitScore(points: Long) {
        val id = configured(R.string.leaderboard_weekly_rush) ?: return
        if (signedIn) PlayGames.getLeaderboardsClient(a).submitScore(id, points)
    }

    fun showLeaderboard() {
        val id = configured(R.string.leaderboard_weekly_rush) ?: return
        PlayGames.getLeaderboardsClient(a).getLeaderboardIntent(id)
            .addOnSuccessListener { a.playGamesUi.launch(it) }
    }

    /** key is the game's achievement key, e.g. "lv10" -> string resource "achievement_lv10". */
    fun unlock(key: String) {
        if (!signedIn) return
        val res = a.resources.getIdentifier("achievement_$key", "string", a.packageName)
        if (res == 0) return
        val id = configured(res) ?: return
        PlayGames.getAchievementsClient(a).unlock(id)
    }

    fun showAchievements() {
        PlayGames.getAchievementsClient(a).achievementsIntent
            .addOnSuccessListener { a.playGamesUi.launch(it) }
    }

    fun saveCloud(json: String) {
        if (!signedIn) return
        val client = PlayGames.getSnapshotsClient(a)
        client.open(saveName, true, SnapshotsClient.RESOLUTION_POLICY_HIGHEST_PROGRESS)
            .addOnSuccessListener { r ->
                val snap = r.data ?: return@addOnSuccessListener
                snap.snapshotContents.writeBytes(json.toByteArray(Charsets.UTF_8))
                val level = runCatching { org.json.JSONObject(json).optLong("maxLv", 1) }.getOrDefault(1)
                val meta = SnapshotMetadataChange.Builder()
                    .setDescription("Pop Quest, level $level")
                    .setProgressValue(level)
                    .build()
                client.commitAndClose(snap, meta)
            }
    }

    private fun loadCloud() {
        val client = PlayGames.getSnapshotsClient(a)
        client.open(saveName, true, SnapshotsClient.RESOLUTION_POLICY_HIGHEST_PROGRESS)
            .addOnSuccessListener { r ->
                val snap = r.data ?: return@addOnSuccessListener
                val bytes = runCatching { snap.snapshotContents.readFully() }.getOrNull()
                client.discardAndClose(snap)
                if (bytes != null && bytes.isNotEmpty()) a.js("onCloudLoad", String(bytes, Charsets.UTF_8))
            }
    }
}
