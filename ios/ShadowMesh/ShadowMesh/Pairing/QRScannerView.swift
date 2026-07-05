import SwiftUI
import AVFoundation
import uniffi_shadowmesh

struct QRScannerView: View {
    @ObservedObject var viewModel: VPNManagerViewModel
    @State private var scannedCode: String?
    @State private var showPinPrompt = false
    @State private var pairingPin: String = ""
    @State private var isPairing = false
    @Environment(\.presentationMode) var presentationMode
    
    var body: some View {
        ZStack {
            if showPinPrompt {
                VStack(spacing: 30) {
                    Text("Enter Pairing PIN")
                        .font(.title2)
                        .fontWeight(.bold)
                    
                    SecureField("6-digit PIN", text: $pairingPin)
                        .keyboardType(.numberPad)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .padding(.horizontal, 40)
                    
                    Button(action: decryptAndPair) {
                        if isPairing {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text("Decrypt & Pair")
                                .fontWeight(.bold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                    .padding(.horizontal, 40)
                    .disabled(pairingPin.count != 6 || isPairing)
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(20)
                .shadow(radius: 20)
                .padding()
            } else {
                QRCameraView(scannedCode: $scannedCode)
                    .edgesIgnoringSafeArea(.all)
                
                VStack {
                    HStack {
                        Spacer()
                        Button(action: {
                            presentationMode.wrappedValue.dismiss()
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 30))
                                .foregroundColor(.white)
                                .padding()
                        }
                    }
                    Spacer()
                    Text("Align QR code within frame to scan")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(10)
                        .padding(.bottom, 50)
                }
            }
        }
        .onChange(of: scannedCode) { newCode in
            if newCode != nil {
                withAnimation {
                    showPinPrompt = true
                }
            }
        }
    }
    
    private func decryptAndPair() {
        guard let code = scannedCode else { return }
        isPairing = true

        Task {
            do {
                let ciphertext = hexToBytes(code)
                let decrypted = decryptQrPairingPayload(ciphertext: ciphertext, pin: pairingPin)

                guard let json = try? JSONSerialization.jsonObject(with: decrypted) as? [String: Any],
                      let serverUrl = json["server_url"] as? String,
                      let handshakeSecret = json["handshake_secret"] as? String,
                      let desktopPubKey = json["desktop_public_key"] as? String else {
                    throw NSError(domain: "Pairing", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid QR payload"])
                }

                // 1. Generate DH Keys
                let mobilePrivKey = generateDhPrivateKey()
                let mobilePubKey = computeDhPublicKey(privateKeyHex: mobilePrivKey)

                // 2. Perform Pair request to server
                let pairUrl = URL(string: "\(serverUrl)/api/v1/sessions/pair/\(handshakeSecret)")!
                var request = URLRequest(url: pairUrl)
                request.httpMethod = "POST"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                let body: [String: Any] = ["mobile_public_key": mobilePubKey]
                request.httpBody = try? JSONSerialization.data(withJSONObject: body)

                let (_, response) = try await URLSession.shared.data(for: request)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                    throw NSError(domain: "Pairing", code: 2, userInfo: [NSLocalizedDescriptionKey: "Server pairing failed"])
                }

                // 3. Derive Session Token
                let sharedSecret = computeDhSharedSecret(privateKeyHex: mobilePrivKey, otherPublicKeyHex: desktopPubKey)
                let sessionToken = deriveSessionToken(dhSharedSecretHex: sharedSecret)
                print("✅ Pairing Successful. Session Token: \(sessionToken)")

                isPairing = false
                presentationMode.wrappedValue.dismiss()
            } catch {
                print("❌ Pairing failed: \(error.localizedDescription)")
                isPairing = false
            }
        }
    }

    private func hexToBytes(_ hex: String) -> [UInt8] {
        var bytes = [UInt8]()
        var hex = hex
        while !hex.isEmpty {
            let index = hex.index(hex.startIndex, offsetBy: 2)
            let byteStr = String(hex[..<index])
            hex = String(hex[index...])
            if let byte = UInt8(byteStr, radix: 16) {
                bytes.append(byte)
            }
        }
        return bytes
    }
}

struct QRCameraView: UIViewControllerRepresentable {
    @Binding var scannedCode: String?
    
    func makeUIViewController(context: Context) -> ScannerViewController {
        let controller = ScannerViewController()
        controller.delegate = context.coordinator
        return controller
    }
    
    func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, ScannerViewControllerDelegate {
        var parent: QRCameraView
        
        init(_ parent: QRCameraView) {
            self.parent = parent
        }
        
        func didFind(code: String) {
            parent.scannedCode = code
        }
    }
}

protocol ScannerViewControllerDelegate: AnyObject {
    func didFind(code: String)
}

class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var captureSession: AVCaptureSession!
    var previewLayer: AVCaptureVideoPreviewLayer!
    weak var delegate: ScannerViewControllerDelegate?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black
        captureSession = AVCaptureSession()
        
        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else { return }
        let videoInput: AVCaptureDeviceInput
        
        do {
            videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)
        } catch {
            return
        }
        
        if (captureSession.canAddInput(videoInput)) {
            captureSession.addInput(videoInput)
        } else {
            return
        }
        
        let metadataOutput = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(metadataOutput)) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            metadataOutput.metadataObjectTypes = [.qr]
        } else {
            return
        }
        
        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer.frame = view.layer.bounds
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        
        DispatchQueue.global(qos: .userInitiated).async {
            self.captureSession.startRunning()
        }
    }
    
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        captureSession.stopRunning()
        if let metadataObject = metadataObjects.first,
           let readableObject = metadataObject as? AVMetadataMachineReadableCodeObject,
           let stringValue = readableObject.stringValue {
            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            delegate?.didFind(code: stringValue)
        }
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if (captureSession?.isRunning == false) {
            DispatchQueue.global(qos: .userInitiated).async {
                self.captureSession.startRunning()
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if (captureSession?.isRunning == true) {
            captureSession.stopRunning()
        }
    }
}
