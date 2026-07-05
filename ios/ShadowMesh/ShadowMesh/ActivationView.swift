import SwiftUI

struct ActivationView: View {
    @ObservedObject var viewModel: VPNManagerViewModel
    @State private var activationCode: String = ""

    private enum DS {
        static let bgPrimary   = Color(red: 0.04, green: 0.04, blue: 0.08)
        static let indigo      = Color(red: 0.39, green: 0.40, blue: 0.95)
        static let muted       = Color(red: 0.45, green: 0.45, blue: 0.56)
    }

    var body: some View {
        ZStack {
            DS.bgPrimary.ignoresSafeArea()

            VStack(spacing: 32) {
                Spacer()

                VStack(spacing: 12) {
                    Text(NSLocalizedString("activation_title", comment: ""))
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)

                    Text(NSLocalizedString("activation_subtitle", comment: ""))
                        .font(.system(size: 16))
                        .foregroundColor(DS.muted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }

                TextField("", text: $activationCode, prompt: Text(NSLocalizedString("activation_placeholder", comment: "")).foregroundColor(DS.muted.opacity(0.5)))
                    .font(.system(size: 18, design: .monospaced))
                    .foregroundColor(.white)
                    .padding()
                    .frame(height: 56)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(DS.indigo.opacity(0.3), lineWidth: 1)
                    )
                    .multilineTextAlignment(.center)
                    .textInputAutocapitalization(.characters)
                    .disableAutocorrection(true)
                    .padding(.horizontal, 24)
                    .onChange(of: activationCode) { newValue in
                        if newValue.count > 25 {
                            activationCode = String(newValue.prefix(25))
                        }
                    }

                Button(action: {
                    Task {
                        await viewModel.activate(code: activationCode)
                    }
                }) {
                    HStack {
                        if viewModel.isActivating {
                            ProgressView()
                                .tint(.white)
                                .padding(.trailing, 8)
                        }
                        Text(NSLocalizedString("activation_button", comment: ""))
                            .font(.system(size: 18, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(activationCode.count == 25 ? DS.indigo : DS.indigo.opacity(0.5))
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                .disabled(activationCode.count != 25 || viewModel.isActivating)
                .padding(.horizontal, 24)

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.system(size: 14))
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }

                Spacer()
                Spacer()
            }
        }
    }
}

#Preview {
    ActivationView(viewModel: VPNManagerViewModel())
}
