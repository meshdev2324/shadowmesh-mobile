import SwiftUI

// MARK: - Design Tokens
private enum DS {
    static let bgPrimary   = Color(red: 0.04, green: 0.04, blue: 0.08)
    static let bgCard      = Color.white.opacity(0.04)
    static let borderCard  = Color.white.opacity(0.06)
    static let indigo      = Color(red: 0.39, green: 0.40, blue: 0.95)
    static let purple      = Color(red: 0.66, green: 0.33, blue: 0.95)
    static let green       = Color(red: 0.06, green: 0.73, blue: 0.51)
    static let amber       = Color(red: 0.96, green: 0.73, blue: 0.19)
    static let danger      = Color(red: 1.00, green: 0.30, blue: 0.30)
    static let muted       = Color(red: 0.45, green: 0.45, blue: 0.56)
}

// MARK: - StatusView
struct StatusView: View {
    @ObservedObject var viewModel: VPNManagerViewModel
    @State private var showTrafficModeSheet = false

    var body: some View {
        NavigationStack {
            ZStack {
                DS.bgPrimary.ignoresSafeArea()

                // Ambient blobs
                Circle().fill(DS.indigo.opacity(0.15))
                    .frame(width: 300, height: 300).blur(radius: 60)
                    .offset(x: -150, y: -200).allowsHitTesting(false)
                Circle().fill(DS.purple.opacity(0.10))
                    .frame(width: 250, height: 250).blur(radius: 60)
                    .offset(x: 150, y: 300).allowsHitTesting(false)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 24) {
                        HeaderView()
                        LatencyVisualizer(
                            latency: viewModel.selectedNode?.latency ?? 0,
                            status: viewModel.status
                        )
                        ConnectButtonView(
                            status: viewModel.status,
                            isConnecting: viewModel.isConnecting,
                            action: { Task { await viewModel.toggleConnection() } }
                        )
                        StatusInfoView(status: viewModel.status)
                        if viewModel.status == .connected { SpeedChart() }

                        // Traffic Mode Selector trigger
                        TrafficModeBadge(preference: viewModel.trafficModePreference) {
                            showTrafficModeSheet = true
                        }

                        GuideBannerView()
                        ServersListView(
                            nodes: viewModel.nodes,
                            selectedNode: viewModel.selectedNode,
                            onSelect: viewModel.selectNode,
                            onRefresh: { Task { await viewModel.loadNodes() } }
                        )
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("").navigationBarHidden(true)
            .alert("Error", isPresented: .init(
                get: { viewModel.errorMessage != nil },
                set: { _ in viewModel.errorMessage = nil }
            )) { Button("OK") {} } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .sheet(isPresented: $showTrafficModeSheet) {
                TrafficModeSelectorSheet(
                    current: viewModel.trafficModePreference,
                    onSelect: { pref in
                        viewModel.setTrafficModePreference(pref)
                        showTrafficModeSheet = false
                    }
                )
                .presentationDetents([.height(340)])
                .presentationDragIndicator(.visible)
            }
        }
    }
}

// MARK: - Traffic Mode Badge (tap to open sheet)
struct TrafficModeBadge: View {
    let preference: TrafficModePreference
    let action: () -> Void

    private var label: String {
        switch preference {
        case .auto: return NSLocalizedString("mode_auto", comment: "").uppercased()
        case .speed: return NSLocalizedString("mode_speed", comment: "").uppercased()
        case .stealth: return NSLocalizedString("mode_stealth", comment: "").uppercased()
        }
    }
    private var color: Color {
        switch preference {
        case .auto: return DS.indigo
        case .speed: return DS.green
        case .stealth: return DS.purple
        }
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "slider.horizontal.3")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(color)
                Text(String(format: NSLocalizedString("traffic_mode_label", comment: ""), label))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(DS.muted)
            }
            .padding(16)
            .background(DS.bgCard)
            .cornerRadius(16)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(color.opacity(0.2), lineWidth: 1))
        }
        .accessibilityLabel("Traffic Mode")
        .accessibilityValue(label)
        .accessibilityHint("Double-tap to change traffic mode")
    }
}

// MARK: - Traffic Mode Selector Sheet
struct TrafficModeSelectorSheet: View {
    let current: TrafficModePreference
    let onSelect: (TrafficModePreference) -> Void

    private let modes: [(TrafficModePreference, String, String, Color)] = [
        (.auto,    NSLocalizedString("mode_auto", comment: ""),    NSLocalizedString("mode_auto_desc", comment: ""),        DS.indigo),
        (.speed,   NSLocalizedString("mode_speed", comment: ""),   NSLocalizedString("mode_speed_desc", comment: ""),   DS.green),
        (.stealth, NSLocalizedString("mode_stealth", comment: ""), NSLocalizedString("mode_stealth_desc", comment: ""),  DS.purple),
    ]

    var body: some View {
        VStack(spacing: 20) {
            Text(NSLocalizedString("traffic_mode", comment: ""))
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .padding(.top, 8)

            VStack(spacing: 12) {
                ForEach(modes, id: \.0) { pref, title, subtitle, color in
                    Button(action: { onSelect(pref) }) {
                        HStack(spacing: 14) {
                            Circle().fill(color.opacity(0.15))
                                .frame(width: 40, height: 40)
                                .overlay(Image(systemName: pref == .speed ? "bolt.fill" : pref == .stealth ? "eye.slash.fill" : "sparkles")
                                    .font(.system(size: 16))
                                    .foregroundColor(color))
                            VStack(alignment: .leading, spacing: 3) {
                                Text(title).font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                                Text(subtitle).font(.system(size: 12)).foregroundColor(DS.muted)
                            }
                            Spacer()
                            if current == pref {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(color)
                                    .font(.system(size: 20))
                            }
                        }
                        .padding(14)
                        .background(current == pref ? color.opacity(0.08) : DS.bgCard)
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(current == pref ? color.opacity(0.3) : DS.borderCard, lineWidth: 1))
                    }
                    .accessibilityLabel("\(title) mode")
                    .accessibilityHint(subtitle)
                    .accessibilityAddTraits(current == pref ? .isSelected : [])
                }
            }
            .padding(.horizontal, 20)
            Spacer()
        }
        .background(Color(red: 0.07, green: 0.07, blue: 0.12).ignoresSafeArea())
    }
}

// MARK: - Latency Visualizer
struct LatencyVisualizer: View {
    let latency: Double
    let status: ConnectionStatus
    @State private var pulse: CGFloat = 1

    private func statusColor() -> Color {
        switch status {
        case .error: return DS.danger
        case .connectingDirect: return DS.indigo
        case .connectingFragmented: return DS.amber
        case .connectingReality: return DS.purple
        case .connected: return latency < 50 ? DS.green : latency < 150 ? DS.amber : DS.danger
        default: return DS.muted
        }
    }

    private func latencyText() -> String {
        switch status {
        case .error: return NSLocalizedString("status_error", comment: "")
        case .disconnecting: return NSLocalizedString("status_disconnecting", comment: "")
        case .disconnected: return NSLocalizedString("status_ready", comment: "")
        case .connectingDirect: return NSLocalizedString("status_connecting_phase1", comment: "")
        case .connectingFragmented: return NSLocalizedString("status_connecting_phase2", comment: "")
        case .connectingReality: return NSLocalizedString("status_connecting_phase3", comment: "")
        case .connected: return latency < 50 ? NSLocalizedString("latency_ultra_low", comment: "") : latency < 150 ? NSLocalizedString("latency_stable", comment: "") : NSLocalizedString("latency_high", comment: "")
        }
    }

    var body: some View {
        ZStack {
            Circle().fill(statusColor()).frame(width: 220, height: 220)
                .scaleEffect(pulse).opacity(pulse == 1 ? 0.3 : 0.1)
                .animation(status == .connected
                    ? .easeInOut(duration: 1.2).repeatForever(autoreverses: true)
                    : .default, value: pulse)
                .onAppear { if status == .connected { pulse = 1.2 } }
                .onChange(of: status) { _, s in pulse = s == .connected ? 1.2 : 1 }

            ZStack {
                Circle().stroke(Color(red: 0.14, green: 0.14, blue: 0.22), lineWidth: 5)
                    .frame(width: 180, height: 180)
                Circle()
                    .trim(from: 0, to: 1 - (min(latency, 300) / 300))
                    .stroke(AngularGradient(
                        gradient: Gradient(colors: [statusColor(), statusColor().opacity(0.4)]),
                        center: .center
                    ), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .frame(width: 180, height: 180)
                    .rotationEffect(.degrees(-90))
                VStack(spacing: 4) {
                    Text(status == .connected ? "\(Int(latency))ms" : "--")
                        .font(.system(size: 36, weight: .bold, design: .monospaced))
                        .foregroundColor(statusColor())

                    Text(latencyText())
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray).textCase(.uppercase).tracking(1)

                    if status.name.contains("connecting") {
                        ProgressView(value: progressValue())
                            .tint(statusColor())
                            .frame(width: 80)
                            .scaleEffect(x: 1, y: 0.5, anchor: .center)
                            .padding(.top, 2)
                    }
                }
        }
    }

    private func progressValue() -> Double {
        switch status {
        case .connectingDirect: return 0.33
        case .connectingFragmented: return 0.66
        case .connectingReality: return 0.90
        default: return 0
        }
    }
            }
            .frame(width: 180, height: 180)
            .background(Color(red: 0.06, green: 0.06, blue: 0.11)).clipShape(Circle())
            .overlay(Circle().stroke(Color(red: 0.14, green: 0.14, blue: 0.22), lineWidth: 1))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Latency: \(status == .connected ? "\(Int(latency)) milliseconds" : latencyText())")
    }
}

// MARK: - Speed Chart
struct SpeedChart: View {
    @State private var downloadHistory: [Double] = [0, 20, 15, 40, 35, 50, 45, 60]
    @State private var uploadHistory:   [Double] = [0, 10, 8,  20, 18, 25, 22, 30]

    var body: some View {
        VStack(spacing: 20) {
            HStack {
                SpeedStat(icon: "arrow.down", label: "DOWNLOAD", value: "12.4 Mbps", color: DS.indigo)
                Spacer()
                SpeedStat(icon: "arrow.up",   label: "UPLOAD",   value: "5.2 Mbps",  color: DS.purple)
            }
            ChartView(downloadHistory: downloadHistory, uploadHistory: uploadHistory)
        }
        .padding(20)
        .background(DS.bgCard).cornerRadius(24)
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(DS.borderCard, lineWidth: 1))
        .accessibilityLabel("Speed chart: downloading 12.4 Megabits per second, uploading 5.2")
    }
}

struct SpeedStat: View {
    let icon: String; let label: String; let value: String; let color: Color
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .bold)).foregroundColor(color)
                .frame(width: 36, height: 36).background(color.opacity(0.1)).cornerRadius(10)
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.system(size: 9, weight: .bold)).foregroundColor(.gray).tracking(1)
                Text(value).font(.system(size: 16, weight: .bold, design: .monospaced)).foregroundColor(.white)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label.lowercased()): \(value)")
    }
}

struct ChartView: View {
    let downloadHistory: [Double]
    let uploadHistory:   [Double]
    let chartHeight: CGFloat = 80

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack {
                // Download fill
                Path { p in
                    let mv = max(downloadHistory.max() ?? 100, uploadHistory.max() ?? 100, 100)
                    let sx = w / CGFloat(downloadHistory.count - 1)
                    for (i, v) in downloadHistory.enumerated() {
                        let x = CGFloat(i) * sx
                        let y = chartHeight - (CGFloat(v) / CGFloat(mv)) * chartHeight
                        i == 0 ? p.move(to: .init(x: x, y: y)) : p.addLine(to: .init(x: x, y: y))
                    }
                    p.addLine(to: .init(x: w, y: chartHeight))
                    p.addLine(to: .init(x: 0, y: chartHeight))
                }
                .fill(LinearGradient(colors: [DS.indigo.opacity(0.3), .clear], startPoint: .top, endPoint: .bottom))

                // Download line
                Path { p in
                    let mv = max(downloadHistory.max() ?? 100, uploadHistory.max() ?? 100, 100)
                    let sx = w / CGFloat(downloadHistory.count - 1)
                    for (i, v) in downloadHistory.enumerated() {
                        let x = CGFloat(i) * sx
                        let y = chartHeight - (CGFloat(v) / CGFloat(mv)) * chartHeight
                        i == 0 ? p.move(to: .init(x: x, y: y)) : p.addLine(to: .init(x: x, y: y))
                    }
                }
                .stroke(DS.indigo, lineWidth: 2)

                // Upload line (dashed)
                Path { p in
                    let mv = max(downloadHistory.max() ?? 100, uploadHistory.max() ?? 100, 100)
                    let sx = w / CGFloat(uploadHistory.count - 1)
                    for (i, v) in uploadHistory.enumerated() {
                        let x = CGFloat(i) * sx
                        let y = chartHeight - (CGFloat(v) / CGFloat(mv)) * chartHeight
                        i == 0 ? p.move(to: .init(x: x, y: y)) : p.addLine(to: .init(x: x, y: y))
                    }
                }
                .stroke(DS.purple, style: StrokeStyle(lineWidth: 1.5, dash: [4, 2]))
            }
        }
        .frame(height: chartHeight)
    }
}

// MARK: - Subviews
private extension StatusView {
    struct HeaderView: View {
        var body: some View {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("SHADOWMESH")
                        .font(.system(size: 20, weight: .bold)).foregroundColor(.white).tracking(2)
                    Text(NSLocalizedString("app_tagline", comment: ""))
                        .font(.system(size: 12, weight: .bold)).foregroundColor(.gray).tracking(1)
                }
                Spacer()
                Button(action: {}) {
                    Image(systemName: "arrow.right.square")
                        .foregroundColor(DS.danger).padding(8)
                        .background(DS.danger.opacity(0.1)).cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(DS.danger.opacity(0.2), lineWidth: 1))
                }
                .accessibilityLabel(NSLocalizedString("logout", comment: ""))
                .accessibilityHint(NSLocalizedString("logout", comment: ""))
            }
        }
    }

    struct ConnectButtonView: View {
        let status: ConnectionStatus
        let isConnecting: Bool
        let action: () -> Void
        @State private var ring: CGFloat = 1

        private var isConnected: Bool { status == .connected }
        private var activeColor: Color { isConnected ? DS.green : DS.indigo }
        private var inactiveColor: Color { isConnected ? Color(red: 0.02, green: 0.59, blue: 0.41) : Color(red: 0.31, green: 0.27, blue: 0.90) }

        var body: some View {
            ZStack {
                // Outer pulse ring
                Circle().stroke(activeColor.opacity(0.25), lineWidth: 2)
                    .frame(width: 180, height: 180)
                    .scaleEffect(ring)
                    .opacity(ring == 1 ? 1 : 0)
                    .animation(
                        (isConnected || isConnecting)
                            ? .easeOut(duration: 1.6).repeatForever(autoreverses: false)
                            : .default,
                        value: ring
                    )
                    .onAppear { if isConnected || isConnecting { ring = 1.35 } }
                    .onChange(of: status) { _, s in ring = (s == .connected || isConnecting) ? 1.35 : 1 }
                    .allowsHitTesting(false)

                // Button face
                Button(action: action) {
                    ZStack {
                        Circle().fill(LinearGradient(
                            gradient: Gradient(colors: [activeColor, inactiveColor]),
                            startPoint: .top, endPoint: .bottom
                        ))
                        .frame(width: 140, height: 140)
                        .shadow(color: activeColor.opacity(0.35), radius: 24, x: 0, y: 6)

                        VStack(spacing: 4) {
                            Image(systemName: isConnected ? "shield.fill" : "power")
                                .font(.system(size: 48)).foregroundColor(.white)
                            Text(isConnected ? NSLocalizedString("button_secure", comment: "") : NSLocalizedString("button_on", comment: ""))
                                .font(.system(size: 20, weight: .bold)).foregroundColor(.white).tracking(1)
                        }
                    }
                }
                .disabled(isConnecting)
                .accessibilityLabel(isConnected ? NSLocalizedString("status_disconnecting", comment: "") : NSLocalizedString("status_connecting_phase1", comment: ""))
                .accessibilityAddTraits(isConnected ? .isSelected : [])
            }
        }
    }

    struct StatusInfoView: View {
        let status: ConnectionStatus
        private var isConnected: Bool { status == .connected }
        var body: some View {
            VStack(spacing: 8) {
                Text(status.description)
                    .font(.system(size: 26, weight: .bold)).foregroundColor(.white)
                HStack(spacing: 8) {
                    Circle().fill(isConnected ? DS.green : DS.muted).frame(width: 8, height: 8)
                    Text(isConnected ? NSLocalizedString("status_encrypted", comment: "") : NSLocalizedString("status_not_connected", comment: ""))
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(isConnected ? DS.green : DS.muted)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Status: \(status.description). \(isConnected ? NSLocalizedString("status_encrypted", comment: "") : NSLocalizedString("status_not_connected", comment: ""))")
        }
    }

    struct GuideBannerView: View {
        var body: some View {
            HStack(spacing: 12) {
                Image(systemName: "shield.fill").font(.system(size: 16))
                    .foregroundColor(DS.indigo)
                    .frame(width: 36, height: 36).background(DS.indigo.opacity(0.1)).cornerRadius(18)
                VStack(alignment: .leading, spacing: 2) {
                    Text(NSLocalizedString("max_sovereignty", comment: "")).font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                    Text(NSLocalizedString("always_on_desc", comment: ""))
                        .font(.system(size: 12, weight: .semibold)).foregroundColor(.gray)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 18)).foregroundColor(.gray)
            }
            .padding(16).background(DS.bgCard).cornerRadius(24)
            .overlay(RoundedRectangle(cornerRadius: 24).stroke(DS.indigo.opacity(0.1), lineWidth: 1))
            .accessibilityLabel(NSLocalizedString("max_sovereignty", comment: ""))
        }
    }

    struct ServersListView: View {
        let nodes: [VPNNode]
        let selectedNode: VPNNode?
        let onSelect: (VPNNode) -> Void
        let onRefresh: () -> Void

        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(NSLocalizedString("available_servers", comment: ""))
                        .font(.system(size: 12, weight: .bold)).foregroundColor(.gray).tracking(1)
                    Spacer()
                    Button(action: onRefresh) {
                        Image(systemName: "arrow.clockwise").font(.system(size: 14)).foregroundColor(DS.indigo)
                    }
                    .accessibilityLabel(NSLocalizedString("refresh", comment: ""))
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 15) {
                        ForEach(nodes, id: \.id) { node in
                            ServerCard(node: node, isSelected: selectedNode?.id == node.id)
                                .onTapGesture { onSelect(node) }
                        }
                    }
                }
            }
        }

        struct ServerCard: View {
            let node: VPNNode
            let isSelected: Bool

            var body: some View {
                VStack(spacing: 6) {
                    Image(systemName: "server.rack").font(.system(size: 18))
                        .foregroundColor(isSelected ? DS.indigo : .gray)
                        .frame(width: 36, height: 36)
                        .background(isSelected ? DS.indigo.opacity(0.2) : Color.white.opacity(0.05)).cornerRadius(18)
                    Text(node.name)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(isSelected ? .white : .gray).lineLimit(1)
                    HStack(spacing: 4) {
                        Circle().fill(node.latency < 100 ? DS.green : DS.amber).frame(width: 6, height: 6)
                        Text("\(Int(node.latency))ms")
                            .font(.system(size: 12, weight: .semibold)).foregroundColor(.gray)
                    }
                }
                .padding(.vertical, 16).frame(width: 140)
                .background(DS.bgCard).cornerRadius(24)
                .overlay(RoundedRectangle(cornerRadius: 24)
                    .stroke(isSelected ? DS.indigo.opacity(0.35) : Color.clear, lineWidth: 1))
                .accessibilityLabel("\(node.name) server, \(Int(node.latency)) milliseconds latency")
                .accessibilityAddTraits(isSelected ? .isSelected : [])
                .accessibilityHint("Double-tap to select this server")
            }
        }
    }
}

// MARK: - Preview
#Preview { StatusView() }
