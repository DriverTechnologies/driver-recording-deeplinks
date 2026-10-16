import SwiftUI

@main
struct RecordingSampleApp: App {
    @State private var model = RecordingModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RecordingScreen(model: model)
                .onOpenURL { model.handleReturn($0) }
        }
        .onChange(of: scenePhase) { _, phase in
            Task { await model.scenePhaseChanged(to: phase) }
        }
    }
}
