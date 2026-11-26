import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer, type Server } from 'node:http'
import { createVerify, generateKeyPairSync, type KeyObject } from 'node:crypto'
import type { AddressInfo } from 'node:net'
import { algorithme, attester, creerClient, Jetons, signerJwt, verifierWebhook, type Identite } from '../src/index.ts'

function verifier(jwt: string, publique: KeyObject): Record<string, any> {
  const [e, c, s] = jwt.split('.')
  const entete = JSON.parse(Buffer.from(e, 'base64url').toString())
  const ok = createVerify('SHA256').update(`${e}.${c}`).verify(entete.alg === 'ES256' ? { key: publique, dsaEncoding: 'ieee-p1363' } : publique,
    Buffer.from(s, 'base64url'))
  assert.ok(ok, 'signature du JWT')
  return JSON.parse(Buffer.from(c, 'base64url').toString())
}

async function serveur(gestion: (chemin: string, corps: string, entetes: Record<string, any>) => [number, string]): Promise<[Server, string]> {
  const s = createServer((req, res) => {
    let corps = ''
    req.on('data', (d) => { corps += d })
    req.on('end', () => {
      const [statut, reponse] = gestion(req.url ?? '', corps, req.headers)
      res.writeHead(statut, { 'Content-Type': 'application/json' }).end(reponse)
    })
  })
  await new Promise<void>((ok) => s.listen(0, '127.0.0.1', ok))
  return [s, `http://127.0.0.1:${(s.address() as AddressInfo).port}`]
}

const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 })

test('le jeton est obtenu par assertion signée, mis en cache, renouvelé, et ses refus signalés', async () => {
  const formulaires: URLSearchParams[] = []
  let statut = 200
  const [s, url] = await serveur((chemin, corps) => {
    formulaires.push(new URLSearchParams(corps))
    return [statut, JSON.stringify({ access_token: `jeton-${formulaires.length}`, expires_in: 300 })]
  })
  try {
    let maintenant = Date.parse('2026-11-22T12:00:00Z')
    const identite: Identite = { url, emetteur: 'https://auth.vinasign.ca', clientId: 'app-1', cle: rsa.privateKey, kid: 'k1', portees: ['enveloppes-lire'] }
    const jetons = new Jetons(identite, () => maintenant)
    const [a, b] = await Promise.all([jetons.obtenir(), jetons.obtenir()])
    assert.equal(a, 'jeton-1')
    assert.equal(b, 'jeton-1', 'une seule demande à la fois')
    assert.equal(await jetons.obtenir(), 'jeton-1', 'en cache')
    const f = formulaires[0]
    assert.equal(f.get('grant_type'), 'client_credentials')
    assert.equal(f.get('scope'), 'enveloppes-lire')
    const assertion = verifier(f.get('client_assertion')!, rsa.publicKey)
    assert.deepEqual([assertion.iss, assertion.sub, assertion.aud], ['app-1', 'app-1', 'https://auth.vinasign.ca'])
    maintenant += 271_000
    assert.equal(await jetons.obtenir(), 'jeton-2', 'renouvelé 30 s avant l\'échéance')
    statut = 401
    maintenant += 1_000_000
    await assert.rejects(jetons.obtenir(), /HTTP 401/)
  } finally {
    s.close()
  }
})

test('les appels typés portent le jeton ; l\'émetteur par défaut est l\'adresse de l\'API', async () => {
  const vus: Record<string, any>[] = []
  const [s, url] = await serveur((chemin, corps, entetes) => {
    if (chemin === '/oauth2/token') {
      vus.push({ aud: verifier(new URLSearchParams(corps).get('client_assertion')!, rsa.publicKey).aud })
      return [200, JSON.stringify({ access_token: 'jeton-x' })]
    }
    vus.push({ chemin, autorisation: entetes.authorization })
    return [200, JSON.stringify([{ code: 'langue', libelle: 'Langue', nombreValeurs: 2 }])]
  })
  try {
    const client = creerClient({ url: `${url}/`, clientId: 'app-1', cle: rsa.privateKey, portees: [] })
    const { data } = await client.GET('/api/v1/referentiels', { params: { query: {} } })
    assert.equal(data?.[0].code, 'langue')
    assert.deepEqual(vus, [{ aud: `${url}/` }, { chemin: '/api/v1/referentiels', autorisation: 'Bearer jeton-x' }])
  } finally {
    s.close()
  }
})

test('ES256 au format JOSE ; les clés inadaptées sont refusées', () => {
  const ec = generateKeyPairSync('ec', { namedCurve: 'P-256' })
  assert.equal(algorithme(ec.privateKey), 'ES256')
  assert.equal(verifier(signerJwt(ec.privateKey, { n: 1 }), ec.publicKey).n, 1)
  assert.throws(() => algorithme(generateKeyPairSync('ec', { namedCurve: 'P-384' }).privateKey))
  assert.throws(() => algorithme(generateKeyPairSync('ed25519').privateKey))
})

test('l\'attestation désigne l\'utilisateur et sa seconde preuve', () => {
  const t = Date.parse('2026-11-22T12:00:00Z')
  const r = verifier(attester({ url: 'https://api.vinasign.ca', clientId: 'app-1', cle: rsa.privateKey, portees: [] }, 'medecin-42', ['pwd', 'otp'],
    new Date(t - 60_000), t), rsa.publicKey)
  assert.equal(r.aud, 'https://api.vinasign.ca/attestations')
  assert.equal(r.sub, 'medecin-42')
  assert.deepEqual(r.amr, ['pwd', 'otp'])
  assert.equal(r.auth_time, Math.floor(t / 1000) - 60)
})

test('le webhook se vérifie comme le serveur le signe (vecteur partagé avec le serveur et le SDK Java)', () => {
  const entete = 't=1000,v1=666c03ec35508aac04dd952534a53765722de9e065b99131f1ad2fa957180969'
  const corps = Buffer.from('{"id":"1"}')
  assert.ok(verifierWebhook('secret-de-test', entete, corps, 1_200_000))
  assert.ok(!verifierWebhook('secret-de-test', entete, Buffer.from('{}'), 1_200_000))
  assert.ok(!verifierWebhook('secret-de-test', entete, corps, 2_000_000), 'rejeu ancien')
  assert.ok(!verifierWebhook('secret-de-test', null, corps, 1_200_000))
  assert.ok(!verifierWebhook('secret-de-test', 'v1=00', corps, 1_200_000))
})
