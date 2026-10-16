import Observation
import SwiftUI

@MainActor
@Observable
final class RecordingModel {
    typealias Action = RecordingDeepLink.Action

    private static let lastSentKey = "lastSent"

    private(set) var isDriverInstalled = DriverInstallation.isInstalled
    private(set) var isWaiting = false
    private(set) var status: String?
    private(set) var lastSent = UserDefaults.standard.string(forKey: RecordingModel.lastSentKey).flatMap(Action.init) {
        didSet { UserDefaults.standard.set(lastSent?.rawValue, forKey: Self.lastSentKey) }
    }

    /// Set once the sample has gone to the background while waiting, so a return can be told
    /// apart from Driver never having opened.
    private var leftForDriver = false

    /// Toggles on what was last sent: a return to `source` doesn't prove recording started.
    var nextAction: Action {
        lastSent == .start ? .stop : .start
    }

    func send(_ action: Action, source: String?, dualCamera: Bool) async {
        // Set before opening: the sample can be in the background before `open` returns.
        isWaiting = true
        status = action == .start ? "Waiting for Driver to start, up to ~12s…" : "Waiting for Driver to stop…"

        if await RecordingDeepLink.open(action: action, source: source, dualCamera: dualCamera) {
            lastSent = action
        } else {
            isWaiting = false
            leftForDriver = false
            status = "Driver isn't installed, or the link couldn't be opened in it."
        }
    }

    func handleReturn(_ url: URL) {
        guard url.absoluteString.hasPrefix(RecordingDeepLink.sourceIdentifier) else { return }
        isWaiting = false
        leftForDriver = false
        status = "Driver handed control back after \(lastSent?.rawValue ?? "a recording action")."
    }

    func scenePhaseChanged(to phase: ScenePhase) async {
        switch phase {
        case .background:
            if isWaiting {
                leftForDriver = true
            }
        case .active:
            // Re-check on every return: the user may have just installed Driver.
            isDriverInstalled = DriverInstallation.isInstalled
            guard isWaiting, leftForDriver else { return }
            // Driver's return URL can arrive just after the scene becomes active.
            try? await Task.sleep(for: .seconds(1))
            // Still waiting means Driver didn't open `source`; the user came back by hand.
            guard isWaiting, leftForDriver else { return }
            isWaiting = false
            leftForDriver = false
            status = "Back without a handoff from Driver."
        default:
            break
        }
    }
}
