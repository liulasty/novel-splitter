# Build stage
FROM node:22-alpine AS build

WORKDIR /app

# Copy package.json and install dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the application
COPY . .

# Build the application
RUN npm run build

# Runtime stage
FROM nginx:alpine

# Set default backend URL, can be overridden by docker-compose environment variables
ENV BACKEND_URL="http://backend:8080"

# Copy nginx configuration
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Copy built assets from the build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Expose port 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
