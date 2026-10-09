# RepairTrack app

Flutter app for iOS, Android and the web. Talks to the backend in [`../backend`](../backend).

Stack: Riverpod 3 (state), Dio 5 (REST), GoRouter (navigation), json_serializable (API models),
flutter_secure_storage (tokens), file_picker (uploads), url_launcher (opening documents).

## Run

```bash
# 1. Backend (other terminal)
cd ../backend && docker compose up -d && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# 2. Generate the JSON code (after every model change; the *.g.dart files are not in git)
dart run build_runner build --delete-conflicting-outputs

# 3. Start
flutter run                      # pick an iOS simulator or Android emulator
flutter run -d chrome --web-port 5173   # web; the local backend allows this origin (CORS)
```

The backend URL defaults to `http://localhost:8081` (Android emulator: `http://10.0.2.2:8081`), the port of the backend's `local` profile.
Override with `--dart-define=API_BASE_URL=https://...`.

Plain HTTP is only allowed in debug builds (Android: `src/debug/AndroidManifest.xml`; iOS: local networking only).

## Check

```bash
dart run build_runner build --delete-conflicting-outputs
flutter analyze
flutter test
```

## Structure

```text
lib/
├── core/           config, network (Dio + token refresh), auth (session), routing, theme, widgets
└── features/
    ├── authentication/   login, register, current user
    ├── vehicles/         dashboard, add/claim vehicle, vehicle details, sold
    ├── repairs/          history, mileage warnings, repair details, new record, correct, void, parts
    ├── garages/          register a garage, garage dashboard, plate search, work on behalf of the garage
    ├── documents/        list, open (presigned link) and upload PDF/JPEG/PNG
    └── sharing/          owner share links; public report at /v/{token} (no login, web)
```

Each feature has `data/` (API calls), `domain/` (models), `application/` (Riverpod providers) and
`presentation/` (screens).

## Rules

- The app never sends or decides source type or verification status; the backend does.
- Records are never deleted; voided records stay visible with their reason, corrections show the original value.
- Buttons for correct/void/upload follow `canCorrect`/`canVoid` from the backend; the backend still checks every request.
- Documents open through short-lived presigned links. On the Android emulator start the backend with
  `S3_PUBLIC_ENDPOINT=http://10.0.2.2:3900` so those links work.
- Mileage warnings are shown as inconsistencies, never as fraud.
- Access and refresh tokens live in secure storage only. Refresh tokens are single-use; the network layer
  refreshes once for parallel requests.

## Share links and the public report

The owner creates a link in *Vehicle details → Historie delen*. The link is `{PUBLIC_BASE_URL}/v/{token}`; locally
`PUBLIC_BASE_URL` is `http://localhost:5173`, so start the web app on that port to open links:

```bash
flutter run -d chrome --web-port 5173
```

The web build uses path URLs (`usePathUrlStrategy`). In production the web host must serve `index.html` for every
path (SPA fallback), so `/v/{token}` loads the app.

## Web build for deployment

```bash
flutter build web --release --dart-define=API_BASE_URL=same-origin
docker build -t repairtrack-web .
```

`same-origin`: the web app calls the API on the host it was loaded from (the edge proxy serves the app and `/api`
on one host), so one image serves staging and production. Mobile builds get the API URL from the
`MOBILE_API_BASE_URL` variable in `mobile-release.yml`. See [../docs/operations.md](../docs/operations.md).
