import UIKit

/// Self-contained; copy this file into your app to trigger Driver recordings.
enum RecordingDeepLink {
    /// This app's return URL (see `CFBundleURLTypes` in Info.plist). `source` can also be an
    /// `https` link; without `source`, Driver acts without reopening any app.
    static let sourceIdentifier = "driver-recording-sample://recording"

    enum Action: String {
        case start = "start_recording"
        case stop = "stop_recording"
    }

    /// `source` is percent-encoded in full, so Driver decodes it back unchanged. `dualCamera` sets
    /// `start_recording`'s `dual_camera` for that recording; `nil` leaves the user's own setting.
    static func url(
        action: Action,
        source: String? = sourceIdentifier,
        dualCamera: Bool? = nil
    ) -> URL {
        var items = [URLQueryItem(name: "intent", value: action.rawValue)]
        if let source {
            items.append(URLQueryItem(name: "source", value: source))
        }
        if action == .start, let dualCamera {
            items.append(URLQueryItem(name: "dual_camera", value: String(dualCamera)))
        }

        var components = URLComponents()
        components.scheme = "https"
        components.host = "drivertechnologies.com"
        components.path = "/actions"
        // `queryItems` would leave `source`'s `:` and `/` (and `&`, `=`, `+`) as they are.
        components.percentEncodedQueryItems = items.map {
            URLQueryItem(name: $0.name, value: $0.value?.addingPercentEncoding(withAllowedCharacters: .unreserved))
        }
        // Every part above is valid, so this can't be nil.
        return components.url!
    }

    /// Opens the link in Driver, never in Safari. Returns `false` when Driver isn't installed, or
    /// when the user turned off Driver's links for drivertechnologies.com by opening one in Safari.
    @MainActor
    static func open(
        action: Action,
        source: String? = sourceIdentifier,
        dualCamera: Bool? = nil
    ) async -> Bool {
        await UIApplication.shared.open(
            url(action: action, source: source, dualCamera: dualCamera),
            options: [.universalLinksOnly: true]
        )
    }
}

private extension CharacterSet {
    /// RFC 3986's unreserved characters: the only ones a query value can carry unencoded.
    static let unreserved = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
}
