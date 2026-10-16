# Recording Deep Link Sample (Android)

A minimal Compose app that starts and stops a Driver recording from another app, and checks whether
Driver is ready to record without opening it:

```
https://drivertechnologies.com/actions?intent=start_recording&source=<your-return-url>
https://drivertechnologies.com/actions?intent=stop_recording&source=<your-return-url>
```

The iOS version is in [`ios`](../ios). The links are the same on both platforms; only what's noted
in each README differs.

## Running the sample

Open this folder in Android Studio and run the `app` configuration on a device with Driver 7.1.3 or
later installed.
`gradle/wrapper/gradle-wrapper.properties` pins the Gradle version; run `gradle wrapper` once if you want
`./gradlew` on the command line.

The screen has:

- **A status pill:** Driver's status (see [Checking Driver's status](#checking-drivers-status)),
  re-checked each time the sample comes back to the foreground.
- **The shutter button:** sends `start_recording`, then `stop_recording`. It toggles on the last link it
  sent and is disabled until the sample is back in the foreground. Start is also disabled unless the
  status is `healthy`; stop never is, so a recording in progress can always be ended.
- **Return to:** the app Driver opens when it's done (the `source` parameter). This sample is selected
  by default; any other launcher app can be picked and is sent by package name.
- **Dual camera:** adds `dual_camera=true` or `dual_camera=false` to start links.
- **The link card:** the exact link the button opens, and the `source` Driver decodes from it. It
  follows both options above.

## The deep links

| Parameter | Required | Values | Notes |
| --- | --- | --- | --- |
| `intent` | Yes | `start_recording`, `stop_recording` | The action to perform. |
| `source` | No | A URL, or a registered package name | Where Driver hands control back. See [`source`](#source). |
| `dual_camera` | No | `true`, `false` | `start_recording` only. See [`dual_camera`](#dual_camera). |

Driver on Android ignores any other parameter, including iOS's `type`: it always records video.

### Sending a link

[`RecordingDeepLink.kt`](app/src/main/java/com/drivertechnologies/recordingsample/RecordingDeepLink.kt)
builds and launches the links:

```kotlin
// false when only a browser could open the link, usually because Driver isn't installed.
val launched = context.triggerRecording(RecordingDeepLink.Action.Start, dualCamera = true)
```

- **Build the link with `Uri.Builder`,** as `RecordingDeepLink.uri` does, so `source` is
  percent-encoded; Driver decodes it back. String concatenation breaks a `source` that contains `&`,
  `=` or `?`.
- **Add `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` (Android 11+)** so the link opens in Driver or fails,
  never in a browser. The link is plain `https`, so without the flag a browser opens it when Driver
  isn't installed and the launch still succeeds. Before Android 11 the flag doesn't exist, and a
  browser can still take the link.
- **Launch it and catch `ActivityNotFoundException`** rather than pre-flighting with `resolveActivity`.
  On Android 11+, `resolveActivity` reports Driver as missing unless Driver is declared in your
  `<queries>`.
- **Ignore further taps until your app is back in the foreground,** so one tap can't send two links.

### `source`

Driver tells the two forms apart by the URI scheme: if `source` has one (`scheme:...`), it's a URL;
otherwise, a package name.

- **A URL (recommended):** a custom scheme or an app link. It works immediately, with no registration.
  The sample's own is `RecordingDeepLink.SOURCE_IDENTIFIER`, `driver-recording-sample://recording`,
  registered by the `VIEW` intent filter in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
  Replace it with your own: see [Create deep links](https://developer.android.com/training/app-links/deep-linking)
  and [Verify Android App Links](https://developer.android.com/training/app-links/verify-android-applinks).
- **A package name:** Driver opens that app's launcher screen. It must be registered with Driver
  Technologies first, and takes effect from the next Driver release on Google Play, so allow for that
  lead time.
- **Omitted:** Driver performs the action without reopening your app.

### `dual_camera`

| `dual_camera` | Records |
| --- | --- |
| omitted | With the user's own camera settings in Driver. |
| `true` | Both cameras, if the device can record them together. |
| `false` | The exterior (back) camera alone. |

Driver supports it on Android and iOS. They differ only when the device can't record both cameras
together and the link says `true`:

| Platform | Records |
| --- | --- |
| Android | The front (interior) camera alone. |
| iOS | The exterior (back) camera alone. |

It applies to that one recording and is never saved: once it ends, Driver goes back to the user's own
settings, including for drives the user starts by hand. Driver ignores the parameter on
`stop_recording`, and ignores any value other than `true` or `false` (in any case).

## What Driver does

- **Signed out:** `start_recording` doesn't record. Driver opens its sign-in screen, shows "Sign in to
  Driver to start recording.", and doesn't return to `source`.
- **Missing permissions:** Driver asks for them instead of recording, so the start times out (below).
- **Already recording video:** `start_recording` leaves the current recording running and ignores
  `dual_camera`. Driver still returns to `source`.
- **`stop_recording` with nothing recording:** nothing happens, and Driver still returns to `source`.

## Return behaviour

Driver waits for the recording to be running before it hands control back, so expect a short delay:

| Step | Wait |
| --- | --- |
| After `start_recording`, for the video recording to start | Up to 10s |
| Then, for the camera to settle before Driver goes to the background | 2s |
| After `stop_recording` | 1s |

If recording doesn't start within 10s, Driver stays open and asks the user to retry or go back. Going
back also opens `source`. The usual causes are a denied permission or an unavailable camera.

So a return is not proof that recording started. Check Driver's status before sending the link
instead, as the sample does: it only allows a start when the status is `healthy`. It also toggles on
the last link it sent rather than on the return.

## Checking Driver's status

Driver answers a status check without opening: no screen, and it doesn't come to the foreground. This
isn't a deep link (a `VIEW` intent always opens an activity). It's a call to Driver's ContentProvider,
so you can warn the user before sending a link.

### From your app

[`DriverStatus.kt`](app/src/main/java/com/drivertechnologies/recordingsample/DriverStatus.kt) wraps the
call. It's a blocking call into Driver, so run it off the main thread:

```kotlin
val status = withContext(Dispatchers.IO) { context.queryDriverStatus() }
```

Underneath, it's a single `ContentResolver.call` with the method `status`, read from the result's
`status` key:

```kotlin
val status = contentResolver
    .call("content://com.trydriver.driver.status".toUri(), "status", null, null)
    ?.getString("status")
```

Without `DriverStatus.kt`, catch the `IllegalArgumentException` this throws when Driver isn't
installed.

Your app must also declare Driver's provider in its manifest. Without it, Android 11+ hides Driver and
every check reports `not_installed`:

```xml
<queries>
    <provider android:authorities="com.trydriver.driver.status" />
</queries>
```

| `status` | Meaning |
| --- | --- |
| `healthy` | Signed in, with the permissions recording needs. |
| `unauthenticated` | The user isn't signed in to Driver. |
| `missing_permissions` | Driver lacks camera, location or (if enabled) microphone access. |
| `unavailable` | Driver couldn't answer within 5s. |
| `not_installed` | Reported by `DriverStatus.kt`, not by Driver: Driver isn't installed, or is older than 7.1.3. |

### With adb

```
adb shell content call --uri content://com.trydriver.driver.status --method status
```

It prints the answer without bringing Driver forward, for example:

```
Result: Bundle[{status=healthy}]
```

### Notes

- **Cold start:** if Driver isn't running, Android starts its process in the background to answer.
  That can take a moment, which is why the call must be off the main thread.
- **Privacy:** the answer is one coarse value and nothing user-identifying, because any installed app
  can ask.
- **Mobile only:** the status check is in Driver's mobile app, not its Android Automotive builds.

## What to copy into your app

- [`RecordingDeepLink.kt`](app/src/main/java/com/drivertechnologies/recordingsample/RecordingDeepLink.kt):
  builds the links, including `dual_camera`, and launches them.
- [`DriverStatus.kt`](app/src/main/java/com/drivertechnologies/recordingsample/DriverStatus.kt): the
  status check.
- From [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml):
  - the `VIEW` intent filter, with your own scheme, so Driver can hand control back;
  - the `<provider>` entry in `<queries>`, for the status check.

  The launcher `<intent>` in `<queries>` only serves the sample's **Return to** menu, which lists other
  apps. You don't need it unless you do the same.
