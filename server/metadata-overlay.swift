import AppKit
import CoreImage
import Foundation
import ImageIO
import Vision

guard CommandLine.arguments.count == 4 || CommandLine.arguments.count == 5 else {
    FileHandle.standardError.write(Data("usage: metadata-overlay INPUT OUTPUT LABEL [auto|fit]\n".utf8))
    exit(2)
}

let input = CommandLine.arguments[1]
let output = CommandLine.arguments[2]
let label = CommandLine.arguments[3]
let renderMode = CommandLine.arguments.count == 5 ? CommandLine.arguments[4] : "auto"
guard renderMode == "auto" || renderMode == "fit" else {
    FileHandle.standardError.write(Data("render mode must be auto or fit\n".utf8))
    exit(2)
}

guard
    let imageSource = CGImageSourceCreateWithURL(URL(fileURLWithPath: input) as CFURL, nil),
    let sourceCG = CGImageSourceCreateImageAtIndex(imageSource, 0, nil)
else {
    FileHandle.standardError.write(Data("unable to open input image\n".utf8))
    exit(3)
}

let canvasSize = NSSize(width: 1072, height: 1448)
let sourceSize = NSSize(width: sourceCG.width, height: sourceCG.height)
let source = NSImage(cgImage: sourceCG, size: sourceSize)

let faceRequest = VNDetectFaceRectanglesRequest()
try? VNImageRequestHandler(cgImage: sourceCG, options: [:]).perform([faceRequest])
var detectedFaceRects = (faceRequest.results ?? []).map { face -> NSRect in
    let box = face.boundingBox
    return NSRect(
        x: box.minX * sourceSize.width,
        y: box.minY * sourceSize.height,
        width: box.width * sourceSize.width,
        height: box.height * sourceSize.height
    )
}
if detectedFaceRects.isEmpty,
   let detector = CIDetector(
       ofType: CIDetectorTypeFace,
       context: nil,
       options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
   ) {
    detectedFaceRects = detector.features(in: CIImage(cgImage: sourceCG)).compactMap {
        ($0 as? CIFaceFeature)?.bounds
    }
}
if ProcessInfo.processInfo.environment["PHOTOFRAME_DEBUG_FACES"] == "1" {
    FileHandle.standardError.write(Data("detected faces: \(detectedFaceRects.count)\n".utf8))
}

let fullSourceRect = NSRect(origin: .zero, size: sourceSize)
let targetAspect = canvasSize.width / canvasSize.height
let sourceAspect = sourceSize.width / sourceSize.height
var cropRect = fullSourceRect
if sourceAspect > targetAspect {
    cropRect.size.width = sourceSize.height * targetAspect
    cropRect.origin.x = (sourceSize.width - cropRect.width) / 2
} else {
    cropRect.size.height = sourceSize.width / targetAspect
    cropRect.origin.y = (sourceSize.height - cropRect.height) / 2
}

// Keep faces inside the portrait crop when that is geometrically possible. If a
// group is spread too widely, preserve the whole photograph instead of cutting
// people in half.
var faceBounds: NSRect?
for rect in detectedFaceRects {
    let padded = rect.insetBy(dx: -rect.width * 0.45, dy: -rect.height * 0.65)
        .intersection(fullSourceRect)
    faceBounds = faceBounds.map { $0.union(padded) } ?? padded
}

var useAspectFit = false
if let bounds = faceBounds {
    if bounds.width <= cropRect.width && bounds.height <= cropRect.height {
        cropRect.origin.x = min(
            max(bounds.midX - cropRect.width / 2, 0),
            sourceSize.width - cropRect.width
        )
        cropRect.origin.y = min(
            max(bounds.midY - cropRect.height / 2, 0),
            sourceSize.height - cropRect.height
        )
    } else {
        useAspectFit = true
    }
}
if renderMode == "fit" {
    useAspectFit = true
}
guard let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: Int(canvasSize.width),
    pixelsHigh: Int(canvasSize.height),
    bitsPerSample: 8,
    samplesPerPixel: 4,
    hasAlpha: true,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
) else {
    FileHandle.standardError.write(Data("unable to create output bitmap\n".utf8))
    exit(4)
}
guard let context = NSGraphicsContext(bitmapImageRep: bitmap) else {
    FileHandle.standardError.write(Data("unable to create graphics context\n".utf8))
    exit(4)
}
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
context.imageInterpolation = .high
NSColor.white.setFill()
NSBezierPath(rect: NSRect(origin: .zero, size: canvasSize)).fill()

var imageDestination = NSRect(origin: .zero, size: canvasSize)
if useAspectFit {
    let scale = min(canvasSize.width / sourceSize.width, canvasSize.height / sourceSize.height)
    imageDestination.size = NSSize(width: sourceSize.width * scale, height: sourceSize.height * scale)
    imageDestination.origin = NSPoint(
        x: (canvasSize.width - imageDestination.width) / 2,
        y: (canvasSize.height - imageDestination.height) / 2
    )
    source.draw(in: imageDestination, from: fullSourceRect, operation: .copy, fraction: 1.0)
} else {
    source.draw(
        in: NSRect(origin: .zero, size: canvasSize),
        from: cropRect,
        operation: .copy,
        fraction: 1.0
    )
}

let paragraph = NSMutableParagraphStyle()
paragraph.alignment = .right
paragraph.lineBreakMode = .byTruncatingHead
let bottomMargin = imageDestination.minY
let labelFitsBelowImage = useAspectFit && bottomMargin >= 70
var attributes: [NSAttributedString.Key: Any] = [
    .font: NSFont.systemFont(ofSize: 22, weight: .medium),
    .foregroundColor: labelFitsBelowImage ? NSColor.black : NSColor.white,
    .paragraphStyle: paragraph,
]
if !labelFitsBelowImage {
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.8)
    shadow.shadowOffset = NSSize(width: 0, height: -1)
    shadow.shadowBlurRadius = 2
    attributes[.shadow] = shadow
}

let attributed = NSAttributedString(string: label, attributes: attributes)
let maximumTextWidth: CGFloat = 920
let measured = attributed.boundingRect(
    with: NSSize(width: maximumTextWidth, height: 80),
    options: [.usesLineFragmentOrigin, .usesFontLeading]
).integral
let horizontalPadding: CGFloat = 16
let verticalPadding: CGFloat = 10
let boxWidth = min(maximumTextWidth + horizontalPadding * 2, measured.width + horizontalPadding * 2)
let boxHeight = max(46, measured.height + verticalPadding * 2)
let box = NSRect(x: canvasSize.width - boxWidth - 20, y: 18, width: boxWidth, height: boxHeight)
if !labelFitsBelowImage {
    NSColor.black.withAlphaComponent(0.68).setFill()
    NSBezierPath(roundedRect: box, xRadius: 7, yRadius: 7).fill()
}
let textRect = box.insetBy(dx: horizontalPadding, dy: verticalPadding)
attributed.draw(with: textRect, options: [.usesLineFragmentOrigin, .usesFontLeading])
context.flushGraphics()
NSGraphicsContext.restoreGraphicsState()

guard
    let png = bitmap.representation(using: .png, properties: [:])
else {
    FileHandle.standardError.write(Data("unable to encode output image\n".utf8))
    exit(4)
}

do {
    try png.write(to: URL(fileURLWithPath: output), options: .atomic)
} catch {
    FileHandle.standardError.write(Data("unable to write output image: \(error)\n".utf8))
    exit(5)
}
