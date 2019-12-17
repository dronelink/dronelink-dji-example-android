# DronelinkDJIExample - Android

## Requirements

- DJISDK key: https://developer.dji.com/
- Mapbox access token: https://account.mapbox.com/access-tokens/create
- Dronelink environment key: https://www.dronelink.com/
- Dronelink Kernel (dronelink-kernel.js): https://github.com/dronelink/dronelink-kernel-js
- Mission plan JSON: Export from any mission plan on https://app.dronelink.com/

## Setup

- Update package to match what was registered with DJI in AndroidManifest.xml
- Provide DJISDK key in AndroidManifest.xml
- Provate Mapbox access token in strings.xml
- Provide Dronelink environment key in MainActivity.java
- Copy dronelink-kernel.js to app/src/main/assets

## Author

Dronelink, dev@dronelink.com

## License

DronelinkDJIExample is available under the MIT license. See the LICENSE file for more info.
