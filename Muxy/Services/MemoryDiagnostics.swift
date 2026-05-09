import AppKit
import Darwin
import Foundation
import MetricKit
import os
#if canImport(Darwin)
import Darwin.libproc
#endif

private let logger = Logger(subsystem: "app.muxy", category: "MemoryDiagnostics")

@MainActor
final class MemoryDiagnostics: NSObject {
    static let shared = MemoryDiagnostics()

    nonisolated(unsafe) private static let stampFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate, .withTime]
        return formatter
    }()

    private weak var appState: AppState?
    nonisolated(unsafe) private let isoFormatter = ISO8601DateFormatter()

    func configure(appState: AppState) {
        self.appState = appState
        MXMetricManager.shared.add(self)
        installCrashHandlers()
    }

    func exportReport() -> URL? {
        guard let dir = ensureExportDirectory() else { return nil }
        let stamp = Self.stampFormatter.string(from: Date())
        let url = dir.appendingPathComponent("muxy-diagnostics-\(stamp).txt")
        do {
            try buildExport().write(to: url, atomically: true, encoding: .utf8)
            return url
        } catch {
            logger.error("Failed to write diagnostics export: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private func buildExport() -> String {
        var out = ""
        out += "Muxy Diagnostics Report\n"
        out += "Generated: \(isoFormatter.string(from: Date()))\n"
        out += "App Version: \(appVersion())\n"
        out += "macOS: \(ProcessInfo.processInfo.operatingSystemVersionString)\n"
        out += "Uptime: \(Int(Date().timeIntervalSince(MuxyApp.launchDate)))s\n\n"

        out += currentStateSection()
        out += "\n"
        out += lastCrashSection()
        out += "\n"
        out += metricKitSection()
        out += "\n"
        out += latestCrashReportSection()
        return out
    }

    private func currentStateSection() -> String {
        let metrics = collectMetrics()
        var out = "=== Current State ===\n"
        out += "Process\n"
        out += "  Footprint: \(metrics.footprintMB) MB\n"
        out += "  Peak: \(metrics.peakMB) MB\n"
        out += "  Threads: \(metrics.threadCount)\n"
        out += "  File Descriptors: \(metrics.fdCount)\n"
        out += "  Windows: \(metrics.windowCount)\n"
        out += "Workspace\n"
        out += "  Projects: \(metrics.projectCount)\n"
        out += "  Tabs: \(metrics.tabCount)\n"
        out += "  Expected Panes: \(metrics.paneCount)\n"
        out += "  Live Surfaces: \(metrics.surfaceCount)\n"
        out += "  Live NSViews: \(metrics.viewCount)\n"
        out += "  Leak Indicator: \(metrics.leak)\n"
        out += "Threads (by name)\n"
        for (name, count) in metrics.threadHistogram.sorted(by: { $0.value > $1.value }) {
            out += "  \(name)=\(count)\n"
        }
        return out
    }

    private func lastCrashSection() -> String {
        guard let url = lastCrashURL(),
              let data = try? Data(contentsOf: url),
              let text = String(data: data, encoding: .utf8)
        else {
            return "=== Last Crash Snapshot ===\n(none)\n"
        }
        return "=== Last Crash Snapshot ===\n\(text)\n"
    }

    private func metricKitSection() -> String {
        guard let dir = metricKitDirectory(),
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil),
              !files.isEmpty
        else {
            return "=== MetricKit Diagnostics ===\n(none)\n"
        }
        var out = "=== MetricKit Diagnostics ===\n"
        for file in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) {
            out += "-- \(file.lastPathComponent) --\n"
            if let data = try? Data(contentsOf: file),
               let text = String(data: data, encoding: .utf8)
            {
                out += text
                if !text.hasSuffix("\n") { out += "\n" }
            }
        }
        return out
    }

    private func latestCrashReportSection() -> String {
        guard let library = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
            return "=== Latest macOS Crash Report ===\n(unavailable)\n"
        }
        let dir = library.appendingPathComponent("Logs/DiagnosticReports", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.contentModificationDateKey]
        )) ?? []
        let muxyReports = files
            .filter { $0.lastPathComponent.hasPrefix("Muxy") }
            .sorted {
                let a = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let b = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                return a > b
            }
        guard let latest = muxyReports.first,
              let data = try? Data(contentsOf: latest),
              let text = String(data: data, encoding: .utf8)
        else {
            return "=== Latest macOS Crash Report ===\n(none)\n"
        }
        return "=== Latest macOS Crash Report (\(latest.lastPathComponent)) ===\n\(text)\n"
    }

    private func collectMetrics() -> Metrics {
        let footprint = Self.physFootprintBytes()
        let peak = Self.peakFootprintBytes()
        let threads = Self.threadInfo()
        let fds = Self.fdCount()
        let windows = NSApp?.windows.count ?? 0

        var projectCount = 0
        var tabCount = 0
        var paneCount = 0

        if let appState {
            let groupedByProject = Dictionary(grouping: appState.workspaceRoots) { $0.key.projectID }
            projectCount = groupedByProject.count
            for (_, entries) in groupedByProject {
                for (_, root) in entries {
                    for area in root.allAreas() {
                        tabCount += area.tabs.count
                        for tab in area.tabs where tab.content.pane != nil {
                            paneCount += 1
                        }
                    }
                }
            }
        }

        let surfaceCount = TerminalViewRegistry.shared.liveSurfaceCount
        let viewCount = TerminalViewRegistry.shared.liveViewCount
        let leak = max(viewCount - paneCount, surfaceCount - paneCount)

        return Metrics(
            footprintMB: footprint / 1_048_576,
            peakMB: peak / 1_048_576,
            threadCount: threads.count,
            threadHistogram: threads.histogram,
            fdCount: fds,
            windowCount: windows,
            projectCount: projectCount,
            tabCount: tabCount,
            paneCount: paneCount,
            surfaceCount: surfaceCount,
            viewCount: viewCount,
            leak: leak
        )
    }

    private func appVersion() -> String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }

    nonisolated private func ensureLogDirectory() -> URL? {
        guard let library = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
            return nil
        }
        let dir = library.appendingPathComponent("Logs/Muxy", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            return dir
        } catch {
            logger.error("Failed to create log dir: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    nonisolated private func metricKitDirectory() -> URL? {
        guard let base = ensureLogDirectory() else { return nil }
        let dir = base.appendingPathComponent("metrickit", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    nonisolated private func lastCrashURL() -> URL? {
        ensureLogDirectory()?.appendingPathComponent("last-crash.txt")
    }

    private func ensureExportDirectory() -> URL? {
        let downloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
        return downloads ?? ensureLogDirectory()
    }

    private struct Metrics {
        let footprintMB: Int
        let peakMB: Int
        let threadCount: Int
        let threadHistogram: [String: Int]
        let fdCount: Int
        let windowCount: Int
        let projectCount: Int
        let tabCount: Int
        let paneCount: Int
        let surfaceCount: Int
        let viewCount: Int
        let leak: Int
    }

    private static func physFootprintBytes() -> Int {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        guard result == KERN_SUCCESS else { return 0 }
        return Int(info.phys_footprint)
    }

    private static func peakFootprintBytes() -> Int {
        var usage = rusage()
        guard getrusage(RUSAGE_SELF, &usage) == 0 else { return 0 }
        return Int(usage.ru_maxrss)
    }

    private static func threadInfo() -> (count: Int, histogram: [String: Int]) {
        var threadList: thread_act_array_t?
        var threadCount: mach_msg_type_number_t = 0
        guard task_threads(mach_task_self_, &threadList, &threadCount) == KERN_SUCCESS,
              let list = threadList
        else {
            return (0, [:])
        }
        defer {
            vm_deallocate(
                mach_task_self_,
                vm_address_t(UInt(bitPattern: list)),
                vm_size_t(Int(threadCount) * MemoryLayout<thread_t>.size)
            )
        }
        var histogram: [String: Int] = [:]
        for i in 0 ..< Int(threadCount) {
            let thread = list[i]
            var nameBuf = [CChar](repeating: 0, count: 64)
            if let pthread = pthread_from_mach_thread_np(thread),
               pthread_getname_np(pthread, &nameBuf, nameBuf.count) == 0
            {
                let name = String(cString: nameBuf)
                let key = name.isEmpty ? "(unnamed)" : name
                histogram[key, default: 0] += 1
            } else {
                histogram["(unnamed)", default: 0] += 1
            }
        }
        return (Int(threadCount), histogram)
    }

    private static func fdCount() -> Int {
        let pid = getpid()
        let needed = proc_pidinfo(pid, PROC_PIDLISTFDS, 0, nil, 0)
        guard needed > 0 else { return 0 }
        return Int(needed) / MemoryLayout<proc_fdinfo>.size
    }
}

extension MemoryDiagnostics: MXMetricManagerSubscriber {
    nonisolated func didReceive(_ payloads: [MXDiagnosticPayload]) {
        guard let dir = metricKitDirectory() else { return }
        let stamp = Self.stampFormatter.string(from: Date())
        for (index, payload) in payloads.enumerated() {
            let url = dir.appendingPathComponent("diagnostic-\(stamp)-\(index).json")
            try? payload.jsonRepresentation().write(to: url, options: .atomic)
        }
    }

    nonisolated func didReceive(_ payloads: [MXMetricPayload]) {
        guard let dir = metricKitDirectory() else { return }
        let stamp = Self.stampFormatter.string(from: Date())
        for (index, payload) in payloads.enumerated() {
            let url = dir.appendingPathComponent("metric-\(stamp)-\(index).json")
            try? payload.jsonRepresentation().write(to: url, options: .atomic)
        }
    }
}

private func writeLastCrashSnapshot(reason: String) {
    guard let library = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
        return
    }
    let dir = library.appendingPathComponent("Logs/Muxy", isDirectory: true)
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    let url = dir.appendingPathComponent("last-crash.txt")
    let when = ISO8601DateFormatter().string(from: Date())
    let pid = getpid()
    let payload = "reason=\(reason)\nat=\(when)\npid=\(pid)\n"
    try? payload.data(using: .utf8)?.write(to: url, options: .atomic)
}

@MainActor
private var didInstallCrashHandlers = false

@MainActor
private extension MemoryDiagnostics {
    func installCrashHandlers() {
        guard !didInstallCrashHandlers else { return }
        didInstallCrashHandlers = true

        NSSetUncaughtExceptionHandler { exception in
            let reason = "uncaught NSException: \(exception.name.rawValue) — \(exception.reason ?? "")"
            writeLastCrashSnapshot(reason: reason)
        }

        let fatalSignals: [Int32] = [SIGABRT, SIGILL, SIGSEGV, SIGFPE, SIGBUS, SIGPIPE]
        for sig in fatalSignals {
            signal(sig) { received in
                writeLastCrashSnapshot(reason: "signal \(received)")
                signal(received, SIG_DFL)
                raise(received)
            }
        }
    }
}
