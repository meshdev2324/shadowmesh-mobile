import SwiftUI
import LocalAuthentication

struct PINPadView: View {
    let title: String
    let subtitle: String
    let onPinComplete: (String) -> Void
    let onCancel: () -> Void
    
    @State private var currentPin = ""
    private let maxPinLength = 4
    
    let buttons = [
        ["1", "2", "3"],
        ["4", "5", "6"],
        ["7", "8", "9"],
        ["Cancel", "0", "Del"]
    ]
    
    var body: some View {
        ZStack {
            Color(red: 0.04, green: 0.04, blue: 0.08).ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer()
                
                Text(title)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.white)
                
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                        .padding(.top, 8)
                }
                
                Spacer().frame(height: 48)
                
                HStack(spacing: 20) {
                    ForEach(0..<maxPinLength, id: \.self) { index in
                        Circle()
                            .fill(index < currentPin.count ? Color.white : Color.white.opacity(0.2))
                            .frame(width: 16, height: 16)
                    }
                }
                
                Spacer().frame(height: 64)
                
                VStack(spacing: 20) {
                    ForEach(buttons, id: \.self) { row in
                        HStack(spacing: 24) {
                            ForEach(row, id: \.self) { btn in
                                if btn == "Cancel" {
                                    Button(action: onCancel) {
                                        Text("Cancel")
                                            .foregroundColor(.white)
                                            .frame(width: 80, height: 80)
                                    }
                                } else if btn == "Del" {
                                    Button(action: {
                                        if !currentPin.isEmpty {
                                            currentPin.removeLast()
                                        }
                                    }) {
                                        Image(systemName: "delete.left.fill")
                                            .foregroundColor(.white)
                                            .font(.system(size: 24))
                                            .frame(width: 80, height: 80)
                                            .background(Color.white.opacity(0.1))
                                            .clipShape(Circle())
                                    }
                                } else {
                                    Button(action: {
                                        if currentPin.count < maxPinLength {
                                            currentPin.append(btn)
                                            if currentPin.count == maxPinLength {
                                                onPinComplete(currentPin)
                                                currentPin = ""
                                            }
                                        }
                                    }) {
                                        Text(btn)
                                            .font(.system(size: 32, weight: .medium))
                                            .foregroundColor(.white)
                                            .frame(width: 80, height: 80)
                                            .background(Color.white.opacity(0.1))
                                            .clipShape(Circle())
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer()
            }
            .padding(.horizontal, 24)
        }
    }
}
