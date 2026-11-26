# SDK TypeScript de VinaSign (VS-093)

| Paquet | Pour | Contenu |
|---|---|---|
| [`@vinasign/client`](client/) | Node 22.18 et plus | Appels typés depuis le contrat OpenAPI v1 ([`serveur/contrat/openapi-v1.json`](../../serveur/contrat/openapi-v1.json)), jeton `private_key_jwt`, attestation d'un utilisateur (VS-044), vérification des webhooks (VS-091) |
| [`@vinasign/embed`](embed/) | Navigateur, sans dépendance | Iframe de la session intégrée, avis `postMessage` filtrés par origine (VS-055) |

Aucune règle de sécurité ni liste métier n'est codée dans ces paquets : la sécurité est appliquée par VinaSign, et
les listes viennent des référentiels de l'API.

## Côté serveur de l'application (Node)

```ts
import { createPrivateKey } from 'node:crypto'
import { creerClient, attester, verifierWebhook } from '@vinasign/client'

const identite = {
  url: process.env.VINASIGN_URL!,
  clientId: process.env.VINASIGN_CLIENT_ID!,
  cle: createPrivateKey(process.env.VINASIGN_CLE_PRIVEE!), // coffre de secrets, jamais le dépôt
  portees: ['enveloppes-creer', 'enveloppes-envoyer', 'sessions-creer'],
}
const vinasign = creerClient(identite)

// Session intégrée pour l'utilisateur déjà authentifié par l'application (VS-044, VS-046).
const { data } = await vinasign.POST('/api/v1/envelopes/{id}/sessions', {
  params: { path: { id: enveloppe } },
  body: { signataire, actions: ['signer'], attestation: attester(identite, utilisateur.id, ['pwd', 'otp'], utilisateur.authentifieLe) },
})
// data.url est remis au navigateur, qui l'affiche avec @vinasign/embed.

// Réception d'un webhook : corps brut, signature vérifiée, puis relecture de l'enveloppe par l'API.
const valide = verifierWebhook(process.env.VINASIGN_WEBHOOK_SECRET!, req.headers['vinasign-signature'], corpsBrut)
```

## Côté navigateur : React

```tsx
import { useEffect, useRef } from 'react'
import { integrer, type AvisVinaSign } from '@vinasign/embed'

export function SignatureVinaSign({ url, surAvis }: { url: string; surAvis: (a: AvisVinaSign) => void }) {
  const conteneur = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const integration = integrer({ url, conteneur: conteneur.current!, titre: 'Signer le document', surAvis })
    return () => integration.retirer()
  }, [url, surAvis])
  return <div ref={conteneur} />
}
```

## Côté navigateur : Angular

```ts
import { Component, ElementRef, OnDestroy, effect, input, output, viewChild } from '@angular/core'
import { integrer, type AvisVinaSign, type Integration } from '@vinasign/embed'

@Component({ selector: 'app-signature-vinasign', template: '<div #conteneur></div>' })
export class SignatureVinaSign implements OnDestroy {
  readonly url = input.required<string>()
  readonly avis = output<AvisVinaSign>()
  private readonly conteneur = viewChild.required<ElementRef<HTMLDivElement>>('conteneur')
  private integration?: Integration

  constructor() {
    effect(() => {
      this.integration?.retirer()
      this.integration = integrer({ url: this.url(), conteneur: this.conteneur().nativeElement, titre: 'Signer le document',
        surAvis: (a) => this.avis.emit(a) })
    })
  }

  ngOnDestroy(): void {
    this.integration?.retirer()
  }
}
```

Avis reçus : `ready`, `signed`, `declined`, `error`, `closed`. Ils ne portent aucune donnée personnelle ; relire
l'enveloppe par l'API avant d'agir.
