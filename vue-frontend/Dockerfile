FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM node:20-alpine

WORKDIR /app
RUN npm install -g serve
COPY --from=builder /app/dist ./dist

# serve the dist folder as a single page app on port 8080 or process.env.PORT
CMD ["sh", "-c", "serve -s dist -l ${PORT:-8080}"]
