ARG NGINX_IMAGE=docker.1ms.run/library/nginx:stable-alpine
ARG NODE_IMAGE=docker.1ms.run/library/node:24-alpine
FROM ${NODE_IMAGE} AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM ${NGINX_IMAGE} AS runtime

ENV API_UPSTREAM=http://192.168.5.250:17790 \
    CLIENT_MAX_BODY_SIZE=20m \
    NGINX_ENVSUBST_FILTER="^(API_UPSTREAM|CLIENT_MAX_BODY_SIZE)$"

COPY deploy/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
