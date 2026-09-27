# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
FROM node:24.19.0-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --no-fund
COPY . .
RUN npm run format:check && npm run lint && npm test && npm run build
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
RUN mkdir -p /tmp/nginx && chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
    && sed -i 's@^pid .*;@pid /tmp/nginx.pid;@; /^user /d' /etc/nginx/nginx.conf
USER nginx
EXPOSE 8080
CMD ["nginx","-g","daemon off;"]
