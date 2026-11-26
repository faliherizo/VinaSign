// Renouvellement silencieux du portail (oidc-client-ts) : la réponse du fournisseur, reçue dans un cadre caché, est
// transmise à la page parente de même origine seulement. Fichier séparé : la CSP refuse tout script en ligne.
window.parent.postMessage({ source: 'oidc-client', url: window.location.href, keepOpen: false }, window.location.origin);
