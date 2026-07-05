
import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: VPNManagerViewModel
    @State private var showQRScanner = false
    @State private var showPINSetup = false
    @State private var newDNSServer = "" // For adding new DNS server

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.04, green: 0.04, blue: 0.08).ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        // VPN Guide Button (Top)
                        Button(action: { viewModel.showVPNGuideModal = true }) {
                            HStack(spacing: 12) {
                                Image(systemName: "lightbulb.fill")
                                    .font(.system(size: 16)).foregroundColor(.yellow)
                                    .frame(width: 32, height: 32).background(.yellow.opacity(0.12)).cornerRadius(8)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(NSLocalizedString("settings_vpn_guide", comment: "")).font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
                                    Text(NSLocalizedString("settings_vpn_guide_subtitle", comment: "")).font(.system(size: 12)).foregroundColor(.gray)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundColor(.gray.opacity(0.6))
                            }
                            .padding(16)
                            .background(Color.white.opacity(0.04))
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.06), lineWidth: 1))
                        }

                        // VPN Section
                        SettingsCard(title: NSLocalizedString("settings_vpn_protection", comment: "")) {
                            SettingsToggleRow(
                                icon: "shield.slash.fill", iconColor: Color(red: 0.96, green: 0.73, blue: 0.19),
                                label: NSLocalizedString("settings_kill_switch", comment: ""),
                                subtitle: viewModel.killSwitchError ?? NSLocalizedString("settings_kill_switch_desc", comment: ""),
                                isOn: $viewModel.killSwitchEnabled,
                                isLoading: viewModel.isKillSwitchLoading
                            ) { viewModel.setKillSwitch(viewModel.killSwitchEnabled) }

                            Divider().background(Color.white.opacity(0.06))

                            SettingsNavRow(
                                icon: "arrow.triangle.branch", iconColor: Color.gray,
                                label: NSLocalizedString("settings_split_tunneling", comment: ""),
                                subtitle: NSLocalizedString("settings_not_supported_ios", comment: "")
                            ) {}
                            .disabled(true)
                            .opacity(0.5)

                            Divider().background(Color.white.opacity(0.06))

                            SettingsNavRow(
                                icon: "network", iconColor: Color(red: 0.06, green: 0.73, blue: 0.51),
                                label: NSLocalizedString("settings_custom_dns", comment: ""),
                                subtitle: viewModel.customDNSServers.isEmpty ? NSLocalizedString("settings_custom_dns_default", comment: "") : String(format: NSLocalizedString("settings_custom_dns_count", comment: ""), viewModel.customDNSServers.count)
                            ) { viewModel.showCustomDNSSettings = true }

                            Divider().background(Color.white.opacity(0.06))

                            SettingsPickerRow(
                                icon: "slider.horizontal.3", iconColor: Color(red: 0.39, green: 0.4, blue: 0.95),
                                label: NSLocalizedString("traffic_mode", comment: ""),
                                subtitle: modeSubtitle(viewModel.trafficModePreference),
                                selection: $viewModel.trafficModePreference,
                                options: [
                                    (TrafficModePreference.auto,    NSLocalizedString("mode_auto", comment: "")),
                                    (TrafficModePreference.speed,   NSLocalizedString("mode_speed", comment: "")),
                                    (TrafficModePreference.stealth, NSLocalizedString("mode_stealth", comment: "")),
                                ]
                            ) { viewModel.setTrafficModePreference(viewModel.trafficModePreference) }
                        }

                        // Security Section
                        SettingsCard(title: NSLocalizedString("settings_security", comment: "")) {
                            SettingsToggleRow(
                                icon: "lock.fill", iconColor: Color(red: 0.06, green: 0.73, blue: 0.51),
                                label: NSLocalizedString("settings_security_lock", comment: ""),
                                subtitle: NSLocalizedString("settings_security_lock_desc", comment: ""),
                                isOn: $viewModel.isSecurityLockEnabled
                            ) {}

                            Divider().background(Color.white.opacity(0.06))

                            SettingsNavRow(
                                icon: "keypad", iconColor: Color(red: 0.66, green: 0.33, blue: 0.95),
                                label: NSLocalizedString("settings_set_pin", comment: ""),
                                subtitle: NSLocalizedString("settings_set_pin_desc", comment: "")
                            ) { showPINSetup = true }

                            Divider().background(Color.white.opacity(0.06))

                            SettingsNavRow(
                                icon: "theatermasks.fill", iconColor: Color(red: 1, green: 0.58, blue: 0.1),
                                label: NSLocalizedString("settings_camouflage_mode", comment: ""),
                                subtitle: NSLocalizedString("settings_camouflage_mode_desc", comment: "")
                            ) {
                                viewModel.toggleCamouflageMode(enabled: true)
                            }
                        }

                        // Pairing Section
                        SettingsCard(title: NSLocalizedString("settings_desktop_pairing", comment: "")) {
                            SettingsNavRow(
                                icon: "qrcode.viewfinder", iconColor: Color(red: 0.39, green: 0.4, blue: 0.95),
                                label: NSLocalizedString("settings_scan_qr", comment: ""),
                                subtitle: NSLocalizedString("settings_scan_qr_desc", comment: "")
                            ) { showQRScanner = true }
                        }

                        // About Section
                        SettingsCard(title: NSLocalizedString("settings_about", comment: "")) {
                            HStack {
                                Image(systemName: "info.circle.fill")
                                    .font(.system(size: 16)).foregroundColor(.gray)
                                    .frame(width: 32, height: 32).background(Color.white.opacity(0.05)).cornerRadius(8)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("ShadowMesh")
                                        .font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
                                    Text(NSLocalizedString("settings_about_desc", comment: ""))
                                        .font(.system(size: 12)).foregroundColor(.gray)
                                }
                                Spacer()
                            }
                            .padding(.vertical, 4)
                            .accessibilityLabel("ShadowMesh version 4.4.0")
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle(NSLocalizedString("settings_title", comment: ""))
            .navigationBarTitleDisplayMode(.large)
            .toolbarColorScheme(.dark)
            .sheet(isPresented: $showQRScanner) {
                QRScannerView(viewModel: viewModel)
            }
            .sheet(isPresented: $viewModel.showCustomDNSSettings) {
                CustomDNSSettingsView(customDNSServers: $viewModel.customDNSServers)
            }
            .sheet(isPresented: $viewModel.showVPNGuideModal) {
                VPNGuideModal()
            }
        }
    }

    private func modeSubtitle(_ pref: TrafficModePreference) -> String {
        switch pref {
        case .auto:    return NSLocalizedString("settings_traffic_mode_auto", comment: "")
        case .speed:   return NSLocalizedString("mode_speed_desc", comment: "")
        case .stealth: return NSLocalizedString("mode_stealth_desc", comment: "")
        }
    }
}

// MARK: - Reusable Settings Components
struct SettingsCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title.uppercased())
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.gray).tracking(1.5)

            VStack(spacing: 12) {
                content()
            }
            .padding(16)
            .background(Color.white.opacity(0.04))
            .cornerRadius(20)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.06), lineWidth: 1))
        }
    }
}

struct SettingsToggleRow: View {
    let icon: String; let iconColor: Color
    let label: String; let subtitle: String
    @Binding var isOn: Bool
    let isLoading: Bool
    let onChange: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 15)).foregroundColor(iconColor)
                .frame(width: 32, height: 32).background(iconColor.opacity(0.12)).cornerRadius(8)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
                Text(subtitle).font(.system(size: 12)).foregroundColor(.red.opacity(subtitle.contains("Failed") ? 1 : 0.5))
            }
            Spacer()
            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
            } else {
                Toggle("", isOn: $isOn).labelsHidden().onChange(of: isOn) { _, _ in onChange() }
            }
        }
        .accessibilityLabel("\(label): \(isOn ? "on" : "off")")
        .accessibilityHint(subtitle)
    }
}

struct SettingsNavRow: View {
    let icon: String; let iconColor: Color
    let label: String; let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 15)).foregroundColor(iconColor)
                    .frame(width: 32, height: 32).background(iconColor.opacity(0.12)).cornerRadius(8)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
                    Text(subtitle).font(.system(size: 12)).foregroundColor(.gray)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundColor(.gray.opacity(0.6))
            }
        }
        .accessibilityLabel(label)
        .accessibilityHint(subtitle + ". Double-tap to open.")
    }
}

struct SettingsPickerRow<T: Hashable>: View {
    let icon: String; let iconColor: Color
    let label: String; let subtitle: String
    @Binding var selection: T
    let options: [(T, String)]
    let onChange: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 15)).foregroundColor(iconColor)
                .frame(width: 32, height: 32).background(iconColor.opacity(0.12)).cornerRadius(8)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
                Text(subtitle).font(.system(size: 12)).foregroundColor(.gray)
            }
            Spacer()
            Picker("", selection: $selection) {
                ForEach(options, id: \.0) { val, name in
                    Text(name).tag(val)
                }
            }
            .pickerStyle(.menu)
            .accentColor(iconColor)
            .onChange(of: selection) { _, _ in onChange() }
        }
        .accessibilityLabel("\(label): \(subtitle)")
    }
}

// MARK: - Custom DNS Settings View
struct CustomDNSSettingsView: View {
    @Binding var customDNSServers: [String]
    @Environment(\.dismiss) var dismiss
    @State private var newDNSServer = ""

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.04, green: 0.04, blue: 0.08).ignoresSafeArea()

                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(NSLocalizedString("dns_title", comment: "")).font(.title2.weight(.semibold)).foregroundColor(.white)
                        Text(NSLocalizedString("dns_subtitle", comment: "")).font(.subheadline).foregroundColor(.gray)
                    }.padding(.top, 20)

                    VStack(alignment: .leading, spacing: 8) {
                        TextField("8.8.8.8", text: $newDNSServer)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.decimalPad)
                            .foregroundColor(.white)
                            .padding()
                            .background(Color.white.opacity(0.05))
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.1), lineWidth: 1))

                        Button(action: {
                            let trimmed = newDNSServer.trimmingCharacters(in: .whitespaces)
                            if !trimmed.isEmpty {
                                customDNSServers.append(trimmed)
                                newDNSServer = ""
                            }
                        }) {
                            HStack {
                                Image(systemName: "plus.circle.fill")
                                Text(NSLocalizedString("dns_add_button", comment: ""))
                            }
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .padding()
                            .frame(maxWidth: .infinity)
                            .background(Color(red: 0.39, green: 0.4, blue: 0.95))
                            .cornerRadius(12)
                        }
                    }

                    List {
                        ForEach(customDNSServers, id: \.self) { dns in
                            HStack {
                                Text(dns)
                                    .font(.system(size: 15))
                                    .foregroundColor(.white)
                                Spacer()
                                Button(action: {
                                    customDNSServers.removeAll(where: { $0 == dns })
                                }) {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                }
                            }
                            .listRowBackground(Color.white.opacity(0.03))
                        }
                        .onDelete(perform: deleteDNSServer)
                    }
                    .scrollContentBackground(.hidden)
                    .listStyle(.plain)

                    Spacer()
                }.padding(.horizontal, 20)
            }
            .navigationTitle(NSLocalizedString("dns_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(NSLocalizedString("done", comment: "")) {
                        dismiss()
                    }
                    .font(.system(size: 14, weight: .semibold))
                }
            }
            .toolbarColorScheme(.dark)
        }
    }

    private func deleteDNSServer(at offsets: IndexSet) {
        customDNSServers.remove(atOffsets: offsets)
    }
}

// MARK: - VPN Setup Guide Modal
struct VPNGuideModal: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.04, green: 0.04, blue: 0.08).ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 24) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(NSLocalizedString("guide_kill_switch_title", comment: "")).font(.title3.weight(.semibold)).foregroundColor(.white)
                            Text(NSLocalizedString("guide_kill_switch_desc", comment: "")).font(.body).foregroundColor(.gray)
                        }

                        Divider().background(Color.white.opacity(0.1))

                        VStack(alignment: .leading, spacing: 8) {
                            Text(NSLocalizedString("guide_security_lock_title", comment: "")).font(.title3.weight(.semibold)).foregroundColor(.white)
                            Text(NSLocalizedString("guide_security_lock_desc", comment: "")).font(.body).foregroundColor(.gray)
                        }

                        Divider().background(Color.white.opacity(0.1))

                        VStack(alignment: .leading, spacing: 8) {
                            Text(NSLocalizedString("guide_traffic_modes_title", comment: "")).font(.title3.weight(.semibold)).foregroundColor(.white)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(NSLocalizedString("guide_traffic_auto", comment: "")).foregroundColor(.gray)
                                Text(NSLocalizedString("guide_traffic_speed", comment: "")).foregroundColor(.gray)
                                Text(NSLocalizedString("guide_traffic_stealth", comment: "")).foregroundColor(.gray)
                            }.font(.body)
                        }

                        Divider().background(Color.white.opacity(0.1))

                        VStack(alignment: .leading, spacing: 8) {
                            Text(NSLocalizedString("guide_camouflage_title", comment: "")).font(.title3.weight(.semibold)).foregroundColor(.white)
                            Text(NSLocalizedString("guide_camouflage_desc", comment: "")).font(.body).foregroundColor(.gray)
                        }
                    }.padding(.horizontal, 20)
                     .padding(.top, 20)
                }
            }
            .navigationTitle(NSLocalizedString("settings_vpn_guide", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(NSLocalizedString("done", comment: "")) {
                        dismiss()
                    }
                    .font(.system(size: 14, weight: .semibold))
                }
            }
            .toolbarColorScheme(.dark)
        }
    }
}

#Preview { SettingsView() }
