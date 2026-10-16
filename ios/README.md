# Recording Deep Link Sample (iOS)

A minimal SwiftUI app that starts and stops a Driver recording from another app:

```
https://drivertechnologies.com/actions?intent=start_recording&source=<your-return-url>
https://drivertechnologies.com/actions?intent=stop_recording&source=<your-return-url>
```

The Android version is in [`android`](../android).
The links are the same on both platforms; only what's noted in each README differs.

## Running the sample

Open `RecordingDeepLinkSample.xcodeproj` in Xcode, pick your team under **Signing & Capabilities**, and
run it on a device with Driver installed. It has no dependencies and needs iOS 17.

The screen has:

- **A status pill:** whether Driver is installed (see [Checking for Driver](#checking-for-driver)),
  re-checked each time the sample comes back to the foreground.
- **The shutter button:** sends `start_recording`, then `stop_recording`. It toggles on the last link it
  sent and is disabled while the sample waits for Driver. Start is also disabled unless Driver is
  installed; stop never is, so a recording in progress can always be ended.
- **Return to:** the URL Driver opens when it's done (the `source` parameter): this sample's own, one you
  type in, or none.
- **Dual camera:** adds `dual_camera=true` or `dual_camera=false` to start links.
- **The link card:** the exact link the button opens, and the `source` Driver decodes from it. It
  follows both options above.

## The deep links

| Parameter | Required | Values | Notes |
| --- | --- | --- | --- |
| `intent` | Yes | `start_recording`, `stop_recording` | The action to perform. |
| `source` | No | An `https` universal link, or a registered custom scheme | Where Driver hands control back. See [`source`](#source). |
| `dual_camera` | No | `true`, `false` | `start_recording` only. See [`dual_camera`](#dual_camera). |
| `type` | No | `video` (default), `passive` | `start_recording` only, and iOS only. See [`type`](#type). |

### Sending a link

[`RecordingDeepLink.swift`](RecordingDeepLinkSample/RecordingDeepLink.swift) builds the links and opens
them in Driver:

```swift
// false when Driver isn't installed, or the user turned off Driver's links (below).
let opened = await RecordingDeepLink.open(action: .start, dualCamera: true)
```

- **Percent-encode `source` in full,** as `RecordingDeepLink.url` does; Driver decodes it back. Setting
  `URLComponents.queryItems` isn't enough: it leaves `:`, `/`, `&` and `=` as they are, so a `source`
  containing `&` or `=` breaks the link.
- **Open it with the `universalLinksOnly` option,** so it goes to Driver and never to Safari:

  ```swift
  let opened = await UIApplication.shared.open(link, options: [.universalLinksOnly: true])
  ```

  `opened` is `false` when Driver isn't installed, and also when the user turned off Driver's links by
  choosing to open one in Safari. Long-pressing a drivertechnologies.com link and choosing **Open in
  Driver** turns them back on. Without the option, both cases open the link in Safari instead.
- **Ignore further taps while you wait for Driver,** so one tap can't send two links.

`opened` only confirms that Driver opened. Whether recording started is signalled by Driver returning to
your `source` (see [Return behaviour](#return-behaviour)).

### `source`

Driver goes back to `source` only if it can open it: it asks `canOpenURL` first. If that fails, Driver
performs the action and stays open, and doesn't show its retry prompt either.

- **An `https` universal link (recommended):** works immediately, with no registration. Use one your
  app claims, so it opens your app rather than Safari: see
  [Supporting universal links in your app](https://developer.apple.com/documentation/xcode/supporting-universal-links-in-your-app).
- **A custom scheme:** define it in your Info.plist (see
  [Defining a custom URL scheme for your app](https://developer.apple.com/documentation/xcode/defining-a-custom-url-scheme-for-your-app)),
  then register it with Driver Technologies. iOS only lets Driver open schemes listed in Driver's own
  `LSApplicationQueriesSchemes`, so a new scheme takes effect from the next Driver release on the App
  Store; allow for that lead time. Until then, recordings still start and stop, but Driver doesn't come
  back.
- **Omitted:** Driver performs the action without reopening your app.

The sample's own `source` is `driver-recording-sample://recording` (`RecordingDeepLink.sourceIdentifier`,
registered by `CFBundleURLTypes` in [`Info.plist`](Info.plist)). Driver lists that scheme from the
release that includes it; older Driver builds record but don't come back.

While **Guided Access** is on, Driver can't open any `source`, so it always stays open.

### `dual_camera`

| `dual_camera` | Records |
| --- | --- |
| omitted | With the user's own camera settings in Driver. |
| `true` | Both cameras, if the device can record them together. |
| `false` | The exterior (back) camera alone. |

Driver supports it on iOS and Android. They differ only when the device can't record both cameras
together and the link says `true`:

| Platform | Records |
| --- | --- |
| iOS | The exterior (back) camera alone. |
| Android | The front (interior) camera alone. |

It applies to that one recording and is never saved: once it ends, Driver goes back to the user's own
settings, including for drives the user starts by hand. Driver ignores the parameter on
`stop_recording` and on `passive` drives, and ignores any value other than `true` or `false` (in any
case).

### `type`

| `type` | Records |
| --- | --- |
| omitted, or `video` | A video recording. |
| `passive` | A drive without video (a pocket drive): trip data only, no camera. |

Driver on Android ignores `type` and always records video. Any other value is treated as `video`. The
sample always records video, so it never sends `type`.

## What Driver does

- **Signed out:** `start_recording` doesn't record. Driver shows a "You’re Not Signed In" alert asking the
  user to sign in, and doesn't return to `source`.
- **Missing permissions:** Driver asks for them instead of recording, so the start times out (below).
- **Already recording video:** `start_recording` leaves the current recording running and ignores
  `dual_camera`. Driver still returns to `source`.
- **`stop_recording` with nothing recording:** nothing happens, and Driver still returns to `source`.

## Return behaviour

Driver waits for the recording to be running before it hands control back, so expect a short delay:

| Step | `video` | `passive` |
| --- | --- | --- |
| On a cold start, for Driver to be ready to record | Up to 8s | None |
| For the recording to start | Up to 10s | Up to 5s |
| Then, for the camera to settle before returning | 2s | None |
| After `stop_recording` | Returns straight away | Returns straight away |

After `stop_recording`, the recording finishes saving in Driver after it has returned.

If recording doesn't start in time, Driver stays open and asks the user to retry or go back. Going back
also opens `source`. The usual causes are a denied permission or an unavailable camera.

So a return is not proof that recording started, and no return doesn't prove it failed (see
[`source`](#source)). The sample toggles on the last link it sent rather than on the return.

## Checking for Driver

Unlike Android, iOS has no way for one app to ask another for its status without opening it. Whether
Driver is installed is all your app can check before sending a link. Driver handles the rest when the
link arrives: it asks a signed-out user to sign in, and asks for missing permissions.

[`DriverInstallation.swift`](RecordingDeepLinkSample/DriverInstallation.swift) does the check with
`canOpenURL` on Driver's URL scheme, `com.trydriver.driver`. `canOpenURL` reports `false` for any
scheme your app doesn't declare, so add it to your Info.plist:

```xml
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>com.trydriver.driver</string>
</array>
```

## What to copy into your app

- [`RecordingDeepLink.swift`](RecordingDeepLinkSample/RecordingDeepLink.swift): builds the links,
  including `dual_camera`, and opens them in Driver. For a pocket drive, add a
  `URLQueryItem(name: "type", value: "passive")` alongside the others in `url(action:source:dualCamera:)`.
- [`DriverInstallation.swift`](RecordingDeepLinkSample/DriverInstallation.swift): the install check.
- From [`Info.plist`](Info.plist):
  - `CFBundleURLTypes`, with your own scheme, if Driver hands control back through one (or claim a
    universal link instead);
  - `LSApplicationQueriesSchemes`, for the install check.
