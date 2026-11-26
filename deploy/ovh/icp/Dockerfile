# Autorité de démonstration de VinaSign : les scripts de l'ICP du bac à sable (infra/bacs-a-sable/icp) n'ont besoin que
# d'OpenSSL et de la bibliothèque standard de Python ; image légère, compte sans privilège.
FROM python:3.12-alpine
RUN apk upgrade --no-cache && apk add --no-cache openssl curl && adduser -S -u 10001 icp && mkdir -p /etat && chown icp /etat
USER 10001
CMD ["sh", "/icp/lancer.sh"]
