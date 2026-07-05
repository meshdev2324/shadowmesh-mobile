import Foundation
import UserNotifications

class NotificationManager {
    static let shared = NotificationManager()
    
    private init() {
        requestAuthorization()
    }
    
    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                print("Notification permission granted.")
            } else if let error = error {
                print("Notification permission error: \(error.localizedDescription)")
            }
        }
    }
    
    func sendConnectionStatusNotification(isConnected: Bool, serverName: String) {
        let content = UNMutableNotificationContent()
        if isConnected {
            content.title = "VPN Connected"
            content.body = "Securely connected to \(serverName)."
            content.sound = UNNotificationSound.default
        } else {
            content.title = "VPN Disconnected"
            content.body = "Your connection is no longer secure."
            content.sound = UNNotificationSound.default
        }
        
        let request = UNNotificationRequest(identifier: "vpnStatusChange", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Failed to add notification: \(error.localizedDescription)")
            }
        }
    }
}
