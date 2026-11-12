# Pop Quest: Bubble Shooter — Android app

This folder is a complete Android Studio project. The game itself lives in
`app/src/main/assets/game/index.html` (the same game you played in the browser), and the
Kotlin code connects it to Android:

| Feature | Where | What it uses |
|---|---|---|
| Rewarded and between-level ads | `AdsManager.kt` | Google AdMob + consent form for EU/UK |
| Remove ads, starter pack, coin packs | `BillingManager.kt` | Google Play Billing 8 |
| Sign-in, weekly leaderboard, achievements, cloud save | `GamesManager.kt` | Google Play Games v2 |
| Reminders (lives full, daily gift, event ending, come back) | `Reminders.kt` | Local notifications, no server |
| Vibration, back button, full-screen | `MainActivity.kt` | Android |
| Analytics and crash reports | `Analytics.kt` | Firebase (turns on when you add its config file) |

Package ID: `com.popquest.bubbleshooter` (it can't be changed after you publish).
The app targets Android 16 (API 36) and runs on Android 8.0 and newer.

Everything works out of the box with **Google's test ads**. Steps 3–6 swap in your real accounts.

---

## 1. Install Android Studio (one time)

1. Download Android Studio from developer.android.com/studio and install it with the default options.
   (Windows PC assumed. On a Mac the steps are the same; menus marked *File > Settings* are under *Android Studio > Settings*.)
2. Open Android Studio, choose **Open**, and select this `PopQuest` folder.
3. Wait for **Gradle sync** to finish (the first time downloads a few hundred MB). If Android Studio offers to
   install a missing SDK platform or build tools, click the link and accept.
4. If it suggests upgrading the Android Gradle Plugin, you can accept; the project uses current versions.

## 2. Run it on your phone

1. On the phone: *Settings > About phone*, tap **Build number** 7 times to unlock Developer options.
2. *Settings > Developer options*: turn on **USB debugging**, then connect the phone by USB and allow the prompt.
3. In Android Studio, pick your phone in the device list at the top and press the green **Run ▶** button.

You should see the game full-screen with music. Reward ads show Google's sample ads labelled **"Test Ad"**.
Sign-in and purchases won't work yet; they need steps 4 and 5.

## 3. AdMob (real ads)

1. Create an account at admob.google.com (use the same Google account as Play Console if you can).
2. **Apps > Add app** > Android > "No, not published yet" > name it *Pop Quest: Bubble Shooter*.
3. Create two ad units:
   - **Rewarded** — used for continue +5 shots, extra life, double coins, free boosters, 2× event points.
   - **Interstitial** — the between-level ad. Under *Ad type*, keep **Display** only and untick Video, so these ads stay short
     and can be closed after a few seconds.
4. Open `app/src/main/res/values/strings.xml` and replace the three test IDs with your App ID and two ad unit IDs.
5. **Privacy & messaging > GDPR**: create and publish a consent message. The app already shows it to EU/UK players.
6. After the app is live, add an `app-ads.txt` file to your website as AdMob instructs.

> About ad length: AdMob decides how long each ad is. Display-only interstitials can usually be closed after about 5 seconds.
> Rewarded ads are often video and can run 15–30 seconds; there's no setting to cap them at 10 seconds.
> The game only gives the reward when the player finishes the ad.

**Never tap your own real ads** — AdMob can close your account for it. Keep the test IDs while developing,
or add your phone as a test device in AdMob.

## 4. Google Play Console and in-app products

1. Finish creating your developer account, then **Create app**: name *Pop Quest: Bubble Shooter*, Game, Free.
2. Build a first upload (see step 8) and upload it to **Testing > Internal testing**. Play only lets you create
   products after a build that includes billing has been uploaded.
3. **Monetize > Products > In-app products** — create these, with exactly these IDs, and activate them:

| Product ID | Name | Price |
|---|---|---|
| `remove_ads` | Remove ads | ₹149 |
| `starter_pack` | Starter pack | ₹79 |
| `coins_500` | 500 coins | ₹49 |
| `coins_1200` | 1,200 coins | ₹99 |
| `coins_3500` | 3,500 coins | ₹249 |

The prices shown in the game come from Play automatically, in each player's own currency.

4. **Setup > License testing**: add your Gmail and your testers. Their purchases use test cards and aren't charged.

## 5. Google Play Games (sign-in, leaderboard, achievements, cloud save)

1. In Play Console: **Grow users > Play Games Services > Setup and management > Configuration**, create a new game project.
2. **Credentials**: add an Android credential for `com.popquest.bubbleshooter`. It asks for the SHA-1 fingerprint:
   use the **App signing key** SHA-1 from *Setup > App signing*, and add a second credential with your **upload key** SHA-1
   so builds from Android Studio can sign in too.
3. **Saved games**: turn it on (this is the cloud save).
4. **Leaderboards**: create **Weekly Rush** (numeric, higher is better).
5. **Achievements**: create these eight (add an icon to each):

| Resource name in `games-ids.xml` | Name | Description |
|---|---|---|
| `achievement_lv10` | Getting started | Reach level 10 |
| `achievement_lv50` | World traveller | Reach level 50 |
| `achievement_lv100` | Centurion | Reach level 100 |
| `achievement_hard10` | Tough cookie | Beat 10 hard levels |
| `achievement_pop1k` | Bubble buster | Pop 1,000 bubbles |
| `achievement_ice50` | Ice breaker | Crack 50 ice bubbles |
| `achievement_chain25` | Chain cutter | Break 25 chains |
| `achievement_streak7` | Daily devotee | Play 7 days in a row |

6. Open `app/src/main/res/values/games-ids.xml`: paste the **project ID** (the number at the top of the setup page),
   the leaderboard ID and each achievement ID in place of `REPLACE_ME`.
7. **Testers**: add your testers' Gmail addresses, then **Review and publish** the Play Games configuration.

Note: Play Games' weekly leaderboard resets on Google's schedule; the in-game event resets on Mondays. Players won't
notice, but the two weeks may not line up exactly.

## 6. Firebase analytics and crash reports (recommended)

1. At console.firebase.google.com, create a project and **add an Android app** with package `com.popquest.bubbleshooter`.
2. Download **google-services.json** and put it in the `app` folder (next to `build.gradle.kts`).
3. Sync and run. Firebase turns on automatically. You'll see `level_start`, `level_end` and `ad_watched` events,
   which show where players get stuck and quit.

## 7. Before you publish

- **Support email:** in `app/src/main/assets/game/index.html`, search for `support@yourstudio.com` and replace it.
- **Privacy policy:** the in-game text is a template. Edit it for your app, publish it on a public web page
  (Google Sites is free), and put that link in Play Console under *App content > Privacy policy*.
- **Data safety form** (Play Console > App content): the app collects device or other IDs (advertising), app activity
  (game progress, analytics), app info and performance (crash logs), and purchase history. Data is encrypted in transit.
  Players can ask for deletion by email.
- **Ads:** declare "Yes, my app contains ads".
- **Target audience:** choose **13 and over**. Choosing under-13 brings Families rules that restrict ads.
- **Content rating:** fill in the questionnaire (a bubble shooter normally rates Everyone / 3+).

## 8. Build the release file

1. **Build > Generate Signed App Bundle or APK > Android App Bundle**.
2. Create a new **keystore**. Save the file and both passwords somewhere safe *and* backed up:
   you need them for every future update.
3. Choose **release** and finish. The `.aab` file is in `app/release/`.
4. In Play Console, keep **Play App Signing** turned on and upload the `.aab`.

For each update, raise `versionCode` (1, 2, 3, …) and `versionName` in `app/build.gradle.kts`.

## 9. Testing and launch

- New personal developer accounts must run a **closed test with at least 12 testers opted in for 14 days in a row**
  before production. Create a *Closed testing* track, add the testers' emails, share the opt-in link, and keep them
  opted in for the full 14 days.
- **Store listing**: app icon 512×512, feature graphic 1024×500, at least 2 phone screenshots (4–8 is better),
  short description (80 characters) and full description. A 30-second gameplay video helps a lot.
- Then **Production > Create release**, upload the same `.aab`, and send for review.

## Changing the game later

The whole game is one file: `app/src/main/assets/game/index.html`. Edit it, run again, and the app picks up the changes.
Test in a desktop browser first: the Android-only features fall back to the same simulated versions you saw in the prototype.

Purchases are checked by the app on the phone. That's normal for a game this size; if fraud becomes a problem later,
add a small server to verify purchases with Google.
