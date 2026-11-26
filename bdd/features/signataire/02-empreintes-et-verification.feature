# language: fr
@VINASIGN
Fonctionnalité: Signature d'empreinte et vérification publique
  Une application fait signer l'empreinte d'un document de santé sans le transmettre ; n'importe quel tiers vérifie
  la signature, sans compte.

  @VS-034 @VS-036
  Scénario: Empreinte signée et horodatée, puis vérifiée publiquement
    Quand j'exécute le cas "empreintes.signature-et-verification"
    Alors la plateforme se comporte comme attendu
