# Build stage
FROM node:20-alpine AS build
WORKDIR /app

# Accept build-time env vars for Vite (baked into the bundle at build time)
ARG VITE_API_URL
ARG VITE_API_BASE_URL
ARG VITE_TENANT_ID=demo
ARG VITE_AUTH_AUTHORITY
ARG VITE_AUTH_CLIENT_ID=lms-frontend
ARG VITE_WS_URL

ENV VITE_API_URL=${VITE_API_URL:-$VITE_API_BASE_URL}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL:-$VITE_API_URL}
ENV VITE_TENANT_ID=$VITE_TENANT_ID
ENV VITE_AUTH_AUTHORITY=$VITE_AUTH_AUTHORITY
ENV VITE_AUTH_CLIENT_ID=$VITE_AUTH_CLIENT_ID
ENV VITE_WS_URL=$VITE_WS_URL

COPY package*.json ./
RUN npm ci --frozen-lockfile
COPY . .
RUN npm run build

# Production stage: serve with Nginx
FROM nginx:alpine
RUN rm /etc/nginx/conf.d/default.conf

# Custom Nginx config for SPA routing
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

