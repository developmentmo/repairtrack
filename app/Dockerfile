# syntax=docker/dockerfile:1.7
# RepairTrack web app: production image with the prebuilt Flutter web bundle.
#
# The bundle is built outside Docker (by CI with the pinned Flutter version, or locally):
#   flutter pub get
#   dart run build_runner build --delete-conflicting-outputs
#   flutter build web --release --dart-define=API_BASE_URL=same-origin
#   docker build -t repairtrack-web .
#
# API_BASE_URL=same-origin: the app calls /api on the host it was loaded from, so the same image works for
# staging and production.

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY build/web/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="repairtrack-web" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
