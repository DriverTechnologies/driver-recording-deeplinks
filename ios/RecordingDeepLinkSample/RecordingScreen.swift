import SwiftUI

private extension Color {
    static let record = Color(red: 0.90, green: 0.22, blue: 0.21)
    static let ready = Color(red: 0.26, green: 0.63, blue: 0.28)
}

/// Where Driver hands control back: the link's `source` parameter.
enum ReturnTarget: Hashable {
    case thisApp
    case custom
    case none
}

struct RecordingScreen: View {
    let model: RecordingModel

    @State private var returnTarget = ReturnTarget.thisApp
    @State private var customSource = "https://"
    @State private var dualCamera = false

    private var source: String? {
        switch returnTarget {
        case .thisApp: RecordingDeepLink.sourceIdentifier
        case .custom: customSource
        case .none: nil
        }
    }

    private var label: String {
        model.nextAction == .start ? "Start recording" : "Stop recording"
    }

    /// Starting needs Driver installed; stopping never does, so a running recording can always end.
    private var isStartBlocked: Bool {
        model.nextAction == .start && !model.isDriverInstalled
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: 32) {
                    VStack(spacing: 12) {
                        Text("Driver remote recording")
                            .font(.title2.weight(.semibold))
                        DriverStatusPill(isInstalled: model.isDriverInstalled)
                    }

                    ShutterButton(
                        nextAction: model.nextAction,
                        isWaiting: model.isWaiting,
                        isBlocked: isStartBlocked,
                        label: label
                    ) {
                        Task { await model.send(model.nextAction, source: source, dualCamera: dualCamera) }
                    }

                    Text(statusText)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .contentTransition(.opacity)
                        .animation(.default, value: statusText)

                    RecordingOptions(
                        returnTarget: $returnTarget,
                        customSource: $customSource,
                        dualCamera: $dualCamera
                    )
                    .disabled(model.isWaiting)

                    NextLink(action: model.nextAction, source: source, dualCamera: dualCamera)
                }
                .padding(24)
                // Centers vertically until the content overflows and scrolls.
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
        }
    }

    private var statusText: String {
        if isStartBlocked {
            "Install Driver to start recording."
        } else {
            model.status ?? "Tap to \(label.lowercased()) in Driver."
        }
    }
}

/// A camera-style shutter: a circle that starts, morphing to a square that stops.
private struct ShutterButton: View {
    let nextAction: RecordingDeepLink.Action
    let isWaiting: Bool
    let isBlocked: Bool
    let label: String
    let action: () -> Void

    var body: some View {
        let isStart = nextAction == .start

        Button(action: action) {
            ZStack {
                if isWaiting {
                    // Only animates while waiting on Driver.
                    PhaseAnimator([false, true]) { expanded in
                        Circle()
                            .stroke(Color.record.opacity(expanded ? 0 : 1), lineWidth: 4)
                            .scaleEffect(expanded ? 1.35 : 1)
                    } animation: { expanded in
                        expanded ? .easeOut(duration: 1.4) : nil
                    }
                }
                Circle()
                    .strokeBorder(.quaternary, lineWidth: 8)
                RoundedRectangle(cornerRadius: isStart ? 60 : 14)
                    .fill(Color.record)
                    .frame(width: isStart ? 120 : 64, height: isStart ? 120 : 64)
            }
            .frame(width: 168, height: 168)
            .contentShape(.circle)
            .animation(.spring(duration: 0.35), value: nextAction)
        }
        .buttonStyle(.plain)
        .disabled(isWaiting || isBlocked)
        // Dimmed only when blocked: while waiting, the halo shows the button is busy instead.
        .opacity(isBlocked && !isWaiting ? 0.38 : 1)
        .accessibilityLabel(label)
    }
}

private struct DriverStatusPill: View {
    let isInstalled: Bool

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(isInstalled ? Color.ready : Color.record)
                .frame(width: 8, height: 8)
            Text(isInstalled ? "Driver is installed" : "Driver isn't installed")
                .font(.subheadline.weight(.medium))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.fill.tertiary, in: .capsule)
        .accessibilityElement(children: .combine)
    }
}

private struct RecordingOptions: View {
    @Binding var returnTarget: ReturnTarget
    @Binding var customSource: String
    @Binding var dualCamera: Bool

    var body: some View {
        VStack(spacing: 0) {
            // A menu-style Picker shows only its selection, so the label is laid out beside it.
            LabeledContent("Return to") {
                Picker("Return to", selection: $returnTarget) {
                    Text("This app").tag(ReturnTarget.thisApp)
                    Text("Custom URL").tag(ReturnTarget.custom)
                    Text("Don't return").tag(ReturnTarget.none)
                }
                .pickerStyle(.menu)
                .labelsHidden()
            }
            .padding(.leading, 16)
            .padding(.trailing, 4)
            .padding(.vertical, 8)

            if returnTarget == .custom {
                TextField("https://example.com/back", text: $customSource)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.body.monospaced())
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }

            Divider()
                .padding(.horizontal, 16)

            Toggle(isOn: $dualCamera) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Dual camera")
                    Text("Exterior camera if unsupported")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .frame(maxWidth: .infinity)
        .background(.fill.quaternary, in: .rect(cornerRadius: 16))
    }
}

private struct NextLink: View {
    let action: RecordingDeepLink.Action
    let source: String?
    let dualCamera: Bool

    var body: some View {
        let url = RecordingDeepLink.url(action: action, source: source, dualCamera: dualCamera)
        // `source` is percent-encoded in the link; this is what Driver reads back.
        let decodedSource = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first { $0.name == "source" }?
            .value

        VStack(alignment: .leading, spacing: 4) {
            Text("Tapping opens")
                .font(.caption.weight(.medium))
            Text(url.absoluteString)
                .font(.footnote.monospaced())

            Text("Driver decodes source as")
                .font(.caption.weight(.medium))
                .padding(.top, 8)
            Text(decodedSource ?? "(none: Driver won't return)")
                .font(.footnote.monospaced())
        }
        .textSelection(.enabled)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.fill.quaternary, in: .rect(cornerRadius: 16))
    }
}

#Preview {
    RecordingScreen(model: RecordingModel())
}
