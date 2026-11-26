import { test } from 'node:test'
import assert from 'node:assert/strict'
import { integrer, origineVinaSign, UrlIntegrationInvalide, type AvisVinaSign } from '../src/index.ts'

/** DOM minimal : un document, une fenêtre qui distribue les messages, un conteneur. */
function dom() {
  const ecouteurs = new Set<(e: unknown) => void>()
  const fenetre = {
    addEventListener: (_: string, f: (e: unknown) => void) => ecouteurs.add(f),
    removeEventListener: (_: string, f: (e: unknown) => void) => ecouteurs.delete(f),
  }
  const enfants: unknown[] = []
  const doc = {
    defaultView: fenetre,
    createElement: () => {
      const iframe = { style: {} as Record<string, string>, contentWindow: { nom: 'iframe' }, remove: () => enfants.splice(enfants.indexOf(iframe), 1) } as Record<string, unknown>
      return iframe
    },
  }
  const conteneur = { ownerDocument: doc, appendChild: (x: unknown) => enfants.push(x) }
  const emettre = (e: unknown) => ecouteurs.forEach((f) => f(e))
  return { conteneur: conteneur as unknown as HTMLElement, enfants, ecouteurs, emettre }
}

const URL_SESSION = 'https://signer.vinasign.ca/integre/AbC_123-xyz'

test('l\'origine de VinaSign vient d\'une URL de session https, ou du poste en http', () => {
  assert.equal(origineVinaSign(URL_SESSION), 'https://signer.vinasign.ca')
  assert.equal(origineVinaSign('http://localhost:4301/integre/abc'), 'http://localhost:4301')
  assert.equal(origineVinaSign('http://127.0.0.1:4301/integre/abc'), 'http://127.0.0.1:4301')
  for (const mauvaise of ['pas une url', 'http://signer.vinasign.ca/integre/abc', 'https://signer.vinasign.ca/s/abc', 'https://signer.vinasign.ca/integre/a/b', 'javascript:alert(1)']) {
    assert.throws(() => origineVinaSign(mauvaise), UrlIntegrationInvalide, mauvaise)
  }
})

test('crée une iframe titrée, qui transmet l\'origine parente, et la retire', () => {
  const d = dom()
  const i = integrer({ url: URL_SESSION, conteneur: d.conteneur, titre: 'Signer le contrat' })
  const iframe = i.iframe as unknown as Record<string, unknown>
  assert.equal(iframe.src, URL_SESSION)
  assert.equal(iframe.title, 'Signer le contrat')
  assert.equal(iframe.referrerPolicy, 'origin')
  assert.deepEqual(d.enfants, [iframe])
  assert.equal(d.ecouteurs.size, 1)
  i.retirer()
  assert.deepEqual(d.enfants, [])
  assert.equal(d.ecouteurs.size, 0)
})

test('exige un titre accessible', () => {
  assert.throws(() => integrer({ url: URL_SESSION, conteneur: dom().conteneur, titre: '  ' }), UrlIntegrationInvalide)
})

test('ne remonte que les avis connus, venus de l\'origine de VinaSign et de son iframe', () => {
  const d = dom()
  const recus: AvisVinaSign[] = []
  const i = integrer({ url: URL_SESSION, conteneur: d.conteneur, titre: 'Signer', surAvis: (a) => recus.push(a) })
  const source = (i.iframe as unknown as { contentWindow: unknown }).contentWindow
  const origin = 'https://signer.vinasign.ca'
  d.emettre({ origin, source, data: { source: 'vinasign', avis: 'ready' } })
  d.emettre({ origin: 'https://intrus.ca', source, data: { source: 'vinasign', avis: 'signed' } })
  d.emettre({ origin, source: {}, data: { source: 'vinasign', avis: 'signed' } })
  d.emettre({ origin, source, data: { source: 'autre', avis: 'signed' } })
  d.emettre({ origin, source, data: { source: 'vinasign', avis: 'inconnu' } })
  d.emettre({ origin, source, data: null })
  d.emettre({ origin, source, data: { source: 'vinasign', avis: 'signed' } })
  assert.deepEqual(recus, ['ready', 'signed'])
})

test('sans rappel, un avis est simplement ignoré', () => {
  const d = dom()
  const i = integrer({ url: URL_SESSION, conteneur: d.conteneur, titre: 'Signer' })
  d.emettre({ origin: 'https://signer.vinasign.ca', source: (i.iframe as unknown as { contentWindow: unknown }).contentWindow, data: { source: 'vinasign', avis: 'declined' } })
})
