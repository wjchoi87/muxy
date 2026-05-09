import AppKit
import SwiftUI

struct DiagnosticsCommands: Commands {
    var body: some Commands {
        CommandMenu("Diagnostics") {
            Button("Export Diagnostics Report...") {
                exportReport()
            }
        }
    }

    @MainActor
    private func exportReport() {
        guard let url = MemoryDiagnostics.shared.exportReport() else { return }
        NSWorkspace.shared.activateFileViewerSelecting([url])
    }
}
