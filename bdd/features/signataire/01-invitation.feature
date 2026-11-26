# language: fr
@VINASIGN
Fonctionnalité: Signataire — invitation, lien sécurisé et consentement
  L'envoi d'une enveloppe invite le signataire dont c'est le tour, par un courriel dans sa langue portant un lien
  personnel. Le lien ouvre une session courte ; le consentement à signer électroniquement est explicite et versionné.

  @VS-040 @VS-041 @VS-042 @VS-080
  Scénario: Du courriel d'invitation au consentement et au code, puis l'annulation invalide le lien
    Quand j'exécute le cas "signataire.invitation"
    Alors la plateforme se comporte comme attendu

  @VS-046 @VS-044
  Scénario: Session intégrée éphémère, à usage unique, pour l'utilisateur attesté par l'application
    Quand j'exécute le cas "signataire.session-integree"
    Alors la plateforme se comporte comme attendu
