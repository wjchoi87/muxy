// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "MuxyMobile",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(name: "MuxyShared", targets: ["MuxyShared"]),
    ],
    targets: [
        .target(
            name: "MuxyShared",
            path: "MuxyShared"
        ),
    ]
)
