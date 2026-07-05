import SwiftUI

struct NotesDecoyView: View {
    @State private var notes = [
        "Groceries: Milk, Eggs, Bread",
        "Meeting with John at 2 PM",
        "Call Mom",
        "Workout routine for tomorrow"
    ]
    
    var body: some View {
        NavigationView {
            List(notes, id: \.self) { note in
                VStack(alignment: .leading) {
                    Text(note.components(separatedBy: ":").first ?? note)
                        .font(.headline)
                    Text("No additional text")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("Notes")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {}) {
                        Image(systemName: "square.and.pencil")
                    }
                    .simultaneousGesture(
                        LongPressGesture(minimumDuration: 3.0).onEnded { _ in
                            // Trigger return to main app
                            NotificationCenter.default.post(name: NSNotification.Name("ExitDecoyMode"), object: nil)
                        }
                    )
                }
            }
        }
    }
}
