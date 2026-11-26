# language: fr
@VINASIGN
Fonctionnalité: Accès — applications clientes
  Une application cliente s'authentifie par une assertion signée de sa clé privée et reçoit un jeton de courte durée,
  limité à son organisation et à ses portées.

  @VS-012
  Scénario: L'application obtient un jeton par client credentials et private_key_jwt
    Quand j'exécute le cas "acces.jeton"
    Alors la plateforme se comporte comme attendu
