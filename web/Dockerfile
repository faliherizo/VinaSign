# Image d'une interface (VS-003) : APPLICATION=portail ou signataire. nginx non privilégié (port 8080) ; en-têtes et
# frame-ancestors tirés de l'environnement au démarrage ; config.json monté par environnement.
FROM node:22-alpine AS construction
ARG APPLICATION
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npx ng build "${APPLICATION}"

FROM nginxinc/nginx-unprivileged:1.27-alpine
ARG APPLICATION
# Défini, même vide : nginx ne substitue que les variables définies ; une variable absente laisserait « ${…} » dans la CSP.
ENV VINASIGN_FOURNISSEUR_IDENTITE=""
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=construction /web/dist/${APPLICATION}/browser/ /usr/share/nginx/html/
USER 101
EXPOSE 8080
