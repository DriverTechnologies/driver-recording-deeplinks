import UIKit

/// iOS doesn't let one app ask another anything without opening it, so whether Driver is
/// installed is all a partner app can check up front. The scheme must be in your Info.plist's
/// `LSApplicationQueriesSchemes`, or `canOpenURL` reports `false` for it.
enum DriverInstallation {
    static let urlScheme = "com.trydriver.driver"

    @MainActor
    static var isInstalled: Bool {
        URL(string: "\(urlScheme)://").map(UIApplication.shared.canOpenURL) ?? false
    }
}
