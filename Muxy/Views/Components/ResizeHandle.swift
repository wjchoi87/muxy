import AppKit
import SwiftUI

struct ResizeHandle: View {
    enum Axis {
        case horizontal
        case vertical
    }

    let axis: Axis
    let onDrag: (DragGesture.Value) -> Void
    @State private var hovering = false

    var body: some View {
        Color.clear
            .frame(
                width: axis == .horizontal ? UIMetrics.resizeHandleHitArea : nil,
                height: axis == .vertical ? UIMetrics.resizeHandleHitArea : nil
            )
            .overlay(
                Rectangle()
                    .fill(hovering ? MuxyTheme.accent : MuxyTheme.border)
                    .frame(width: axis == .horizontal ? 1 : nil, height: axis == .vertical ? 1 : nil)
            )
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 1)
                    .onChanged(onDrag)
            )
            .onHover { on in
                hovering = on
                if on {
                    cursor.push()
                } else {
                    NSCursor.pop()
                }
            }
    }

    private var cursor: NSCursor {
        axis == .horizontal ? .resizeLeftRight : .resizeUpDown
    }
}
