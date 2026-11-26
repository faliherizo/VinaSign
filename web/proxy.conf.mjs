// Relais de développement : /api vers le serveur VinaSign. L'adresse vient de l'environnement (VINASIGN_API_URL) ;
// la valeur de repli ne sert que sur un poste, au port documenté dans .env.exemple.
export default {
  '/api': { target: process.env.VINASIGN_API_URL ?? `http://localhost:${process.env.VINASIGN_PORT ?? 8300}`, secure: false, changeOrigin: true },
};
