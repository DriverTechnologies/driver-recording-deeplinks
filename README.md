# Driver recording deep links

Start and stop a [Driver](https://drivertechnologies.com) recording from your own app with a link:

```
https://drivertechnologies.com/actions?intent=start_recording&source=<your-return-url>
https://drivertechnologies.com/actions?intent=stop_recording&source=<your-return-url>
```

Driver records, then hands control back to `source`. The links are the same on Android and iOS. Each
folder has a sample app and the full documentation for that platform:

| Platform | Sample | Also covers |
| --- | --- | --- |
| [Android](android) | Jetpack Compose | Checking whether Driver is signed in and ready, without opening it. |
| [iOS](ios) | SwiftUI | Checking whether Driver is installed. |

Copy the helper files each README lists into your app; they have no dependencies.

## License

[MIT](LICENSE)
