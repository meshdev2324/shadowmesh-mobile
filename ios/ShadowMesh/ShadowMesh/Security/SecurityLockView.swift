import SwiftUI
import LocalAuthentication

struct SecurityLockView: View {
    @Binding var isUnlocked: Bool
    @State private var pin: String = ""
    @State private var attempts: Int = 0
    @State private var isShaking: Bool = false
    
    private let duressPin = "999999"

    var body: some View {
        ZStack {
            // Glassmorphism background
            Color.black.opacity(0.85).edgesIgnoringSafeArea(.all)
            
            VStack(spacing: 40) {
                Image(systemName: "lock.shield.fill")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)
                    .foregroundColor(.blue)
                
                Text("Enter PIN")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                
                HStack(spacing: 15) {
                    ForEach(0..<6, id: \.self) { index in
                        Circle()
                            .fill(index < pin.count ? Color.blue : Color.gray.opacity(0.3))
                            .frame(width: 15, height: 15)
                    }
                }
                .offset(x: isShaking ? -10 : 0)
                .animation(.default.repeatCount(3).speed(3), value: isShaking)
                
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 20) {
                    ForEach(1...9, id: \.self) { number in
                        Button(action: { type(number: number) }) {
                            Text("\(number)")
                                .font(.title)
                                .foregroundColor(.white)
                                .frame(width: 70, height: 70)
                                .background(Color.white.opacity(0.1))
                                .clipShape(Circle())
                        }
                    }
                    
                    Button(action: authenticateWithBiometrics) {
                        Image(systemName: "faceid")
                            .font(.title)
                            .foregroundColor(.white)
                    }
                    
                    Button(action: { type(number: 0) }) {
                        Text("0")
                            .font(.title)
                            .foregroundColor(.white)
                            .frame(width: 70, height: 70)
                            .background(Color.white.opacity(0.1))
                            .clipShape(Circle())
                    }
                    
                    Button(action: delete) {
                        Image(systemName: "delete.left")
                            .font(.title)
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 40)
            }
        }
        .onAppear(perform: authenticateWithBiometrics)
    }
    
    private func type(number: Int) {
        if pin.count < 6 {
            pin.append("\(number)")
            if pin.count == 6 {
                verifyPin()
            }
        }
    }
    
    private func delete() {
        if !pin.isEmpty {
            pin.removeLast()
        }
    }
    
    private func verifyPin() {
        let storedSalt = try? SecureStorage.shared.getString(forKey: SecureStorage.shared.KEY_PIN_SALT)
        let storedHash = try? SecureStorage.shared.getString(forKey: "pin_hash")
        
        let panicStoredSalt = try? SecureStorage.shared.getString(forKey: SecureStorage.shared.KEY_PANIC_PIN_SALT)
        let panicStoredHash = try? SecureStorage.shared.getString(forKey: "panic_pin_hash")

        // Check duress PIN first
        if let panicSalt = panicStoredSalt, let panicHash = panicStoredHash {
            let hashedPanicInput = SecureStorage.shared.hashPin(pin, salt: panicSalt)
            if hashedPanicInput == panicHash {
                PanicWipeManager.shared.executePanicWipe()
                return
            }
        }

        // Check normal PIN
        if let salt = storedSalt, let hash = storedHash {
            let hashedInput = SecureStorage.shared.hashPin(pin, salt: salt)
            if hashedInput == hash {
                withAnimation {
                    isUnlocked = true
                }
                return
            }
        } else {
            // Default to "123456" for demo if no PIN set
            let defaultSalt = SecureStorage.shared.generateSalt()
            let defaultHash = SecureStorage.shared.hashPin("123456", salt: defaultSalt)
            let hashedInputWithDefault = SecureStorage.shared.hashPin(pin, salt: defaultSalt)
            if hashedInputWithDefault == defaultHash || pin == "123456" {
                withAnimation {
                    isUnlocked = true
                }
                return
            }
        }
        
        // Also support plain duress pin for backwards compatibility
        if pin == duressPin {
            PanicWipeManager.shared.executePanicWipe()
            return
        }

        attempts += 1
        isShaking = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            pin = ""
            isShaking = false
        }
    }
    
    private func authenticateWithBiometrics() {
        let context = LAContext()
        var error: NSError?
        
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            let reason = "Unlock ShadowMesh"
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authError in
                DispatchQueue.main.async {
                    if success {
                        withAnimation {
                            self.isUnlocked = true
                        }
                    }
                }
            }
        }
    }
}
