# Yingke · AI Short Drama Platform

Yingke is an AI short-drama platform with a mobile H5 player, desktop administration console, and Java API. The frontend can later be embedded in an App WebView.

## Repository layout

```text
shortdrama-backend/   Java backend, admin API, database scripts, and integration docs
shortdrama-frontend/  H5 playback experience and administration pages
```

The H5 player and admin console share the frontend project and are served at `/` and `/admin`.

## Requirements

- Node.js 20.19+
- Java 21+
- Maven 3.9+
- MySQL
- ffprobe for source-video duration detection

## Local development

For a new environment, copy `shortdrama-backend/application-local.example.yml` to `shortdrama-backend/application-local.yml` and configure the database and initial administrator password.

Start the backend from the repository root:

```bash
python3 shortdrama-backend/scripts/start_backend.py
```

In another terminal:

```bash
cd shortdrama-frontend
npm install
npm run dev
```

The H5 player is available at http://127.0.0.1:5173/ and the admin console at `/admin`.

Do not commit local uploads, database files, build output, passwords, or signing keys. See the Chinese README and `docs/` for product, architecture, and integration details.
