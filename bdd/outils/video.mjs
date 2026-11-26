#!/usr/bin/env node
/**
 * Vidéo de démonstration de VinaSign, tournée à partir du test demo/demonstration.spec.ts (vrai contrat PDF, données de
 * donnees/demonstration.json) : ce que la vidéo montre, le test l'a vérifié. Narration en français (voix macOS
 * « Amélie »), sous-titres WebVTT, cartons de titre rendus par Chromium (l'ffmpeg de macOS n'a pas toujours drawtext).
 *
 *   node outils/video.mjs voix       clips de narration et leurs durées (cache : medias/.voix/)
 *   node outils/video.mjs tourner    tournage (plateforme démarrée comme pour la BDD)
 *   node outils/video.mjs monter     voix et cartons posés, MP4 + VTT + affiche dans medias/demo/
 *   node outils/video.mjs publier    envoi de medias/demo vers le serveur (deploy/ovh/medias, monté dans le portail)
 *
 * medias/ n'est pas versionné : la vidéo est publiée sur le serveur (deploy/ovh/medias, voir docs/exploitation).
 */
import { execFileSync, spawnSync } from 'child_process'
import { createHash } from 'crypto'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'

const BDD = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const MEDIAS = join(BDD, 'medias')
const VOIX = join(MEDIAS, '.voix')
const DUREES = join(VOIX, 'durees.json')
const DONNEES = JSON.parse(readFileSync(join(BDD, 'donnees/demonstration.json'), 'utf8'))
const VOIX_MACOS = process.env.VOIX || 'Amélie'
const CARTONS = [
  { titre: DONNEES.film.titre, sous: 'Plateforme de signature électronique opérée par Vina IT Solution', voix: 'Signer un contrat électroniquement, de bout en bout, avec VinaSign.' },
  { titre: 'Préparer · signer · sceller · prouver', sous: 'vinasign.vinaitsolution.com', voix: 'VinaSign, par Vina I T Solution.' },
]

const ffmpeg = (...a) => execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...a], { stdio: ['ignore', 'inherit', 'inherit'] })
const duree = (f) => Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', f]).toString().trim())
const lire = (f) => JSON.parse(readFileSync(f, 'utf8'))
const hms = (ms) => new Date(Math.max(0, ms)).toISOString().slice(11, 23)
const html = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;')

function clip(texte, durees) {
  const wav = join(VOIX, `${createHash('sha1').update(texte).digest('hex').slice(0, 16)}.wav`)
  if (!existsSync(wav)) {
    const aiff = wav.replace(/\.wav$/, '.aiff')
    execFileSync('say', ['-v', VOIX_MACOS, '-o', aiff, texte])
    ffmpeg('-i', aiff, '-ac', '1', '-ar', '48000', wav)
    rmSync(aiff, { force: true })
  }
  durees[texte] ??= duree(wav)
  return wav
}

function voix() {
  mkdirSync(VOIX, { recursive: true })
  const durees = existsSync(DUREES) ? lire(DUREES) : {}
  for (const t of [...Object.values(DONNEES.narration), ...CARTONS.map((c) => c.voix)]) clip(t, durees)
  writeFileSync(DUREES, JSON.stringify(durees, null, 1))
  console.log(`✓ narration : ${Object.keys(durees).length} clip(s)`)
}

function tourner() {
  if (!existsSync(DUREES)) voix()
  const r = spawnSync('npx', ['playwright', 'test', '-c', 'playwright.demo.config.ts'], { cwd: BDD, stdio: 'inherit',
    env: { ...process.env, DEMO_DUREES: DUREES } })
  process.exitCode = r.status ?? 1
}

/** Carton de titre : image rendue par Chromium (texte qui passe à la ligne), sa narration, au format du tournage. */
async function carton(navigateur, c, durees, sortie) {
  const d = Math.max(4, durees[c.voix] + 1)
  const png = sortie.replace(/\.mp4$/, '.png')
  const page = await navigateur.newPage({ viewport: { width: 1280, height: 720 } })
  await page.setContent(`<body style="margin:0;width:1280px;height:720px;display:flex;flex-direction:column;justify-content:center;align-items:center;
    background:linear-gradient(135deg,#0b3d6b,#145da0);color:#fff;font-family:Arial,Helvetica,sans-serif;text-align:center">
    <div style="font-size:22px;letter-spacing:.3em;color:#bcd6ee;margin-bottom:28px">VINASIGN</div>
    <h1 style="font-size:46px;max-width:1080px;margin:0 0 22px;line-height:1.25">${html(c.titre)}</h1>
    <p style="font-size:26px;color:#dbe8f5;margin:0">${html(c.sous)}</p></body>`)
  await page.screenshot({ path: png })
  await page.close()
  ffmpeg('-loop', '1', '-framerate', '25', '-i', png, '-i', clip(c.voix, durees), '-filter_complex', `[1:a]apad=whole_dur=${d}[a]`,
    '-map', '0:v', '-map', '[a]', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '24', '-pix_fmt', 'yuv420p', '-r', '25',
    '-c:a', 'aac', '-b:a', '96k', '-ar', '48000', '-ac', '1', '-t', String(d), sortie)
  return d
}

async function monter() {
  const t = lire(join(BDD, 'test-results-demo/demonstration.json'))
  if (!existsSync(t.video)) throw new Error(`Vidéo du tournage absente : ${t.video}`)
  const durees = lire(DUREES)
  const travail = join(VOIX, 'montage'); mkdirSync(travail, { recursive: true })
  const total = Math.round(duree(t.video) * 1000)
  // Voix posée à chaque instant de la chronologie, sur un silence de la durée du tournage.
  const voixTournage = join(travail, 'voix.wav')
  const entrees = t.chronologie.flatMap((m) => ['-i', clip(m.voix, durees)])
  const filtres = t.chronologie.map((m, i) => `[${i}:a]adelay=${m.t}|${m.t}[a${i}]`).join(';')
  ffmpeg(...entrees, '-filter_complex', `${filtres};${t.chronologie.map((_, i) => `[a${i}]`).join('')}amix=inputs=${t.chronologie.length}:normalize=0,apad=whole_dur=${total / 1000}[s]`,
    '-map', '[s]', '-ac', '1', '-ar', '48000', voixTournage)
  const tournage = join(travail, 'tournage.mp4')
  ffmpeg('-i', t.video, '-i', voixTournage, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '24', '-pix_fmt', 'yuv420p',
    '-r', '25', '-c:a', 'aac', '-b:a', '96k', '-ar', '48000', '-ac', '1', tournage)
  const { chromium } = await import('@playwright/test')
  const navigateur = await chromium.launch()
  const debut = join(travail, 'debut.mp4'), fin = join(travail, 'fin.mp4')
  const dDebut = await carton(navigateur, CARTONS[0], durees, debut)
  const dFin = await carton(navigateur, CARTONS[1], durees, fin)
  await navigateur.close()
  const sortie = join(MEDIAS, 'demo/vinasign-demo.mp4'); mkdirSync(dirname(sortie), { recursive: true })
  const liste = [debut, tournage, fin]
  ffmpeg(...liste.flatMap((f) => ['-i', f]), '-filter_complex', liste.map((_, i) => `[${i}:v][${i}:a]`).join('') + `concat=n=${liste.length}:v=1:a=1[v][a]`,
    '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '26', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '96k',
    '-movflags', '+faststart', sortie)
  // Sous-titres : cartons et narration, décalés de la durée du carton d'ouverture.
  const decalage = dDebut * 1000
  const lignes = [`${hms(0)} --> ${hms(decalage)}\n${CARTONS[0].voix}`]
  t.chronologie.forEach((m, i) => {
    const suivant = i + 1 < t.chronologie.length ? t.chronologie[i + 1].t : m.t + 7000
    lignes.push(`${hms(m.t + decalage)} --> ${hms(Math.min(suivant, m.t + 9000) + decalage)}\n${m.texte}`)
  })
  lignes.push(`${hms(decalage + total)} --> ${hms(decalage + total + dFin * 1000)}\n${CARTONS[1].voix}`)
  writeFileSync(sortie.replace(/\.mp4$/, '.vtt'), `WEBVTT\n\n${lignes.join('\n\n')}\n`)
  // Affiche : les signatures sur la page 2 du contrat final, juste avant la conclusion (sinon, au milieu du tournage).
  const conclusion = t.chronologie.find((m) => m.texte === DONNEES.narration.fin)
  const instant = (decalage + (conclusion ? conclusion.t - 300 : total / 2)) / 1000
  ffmpeg('-ss', String(instant), '-i', sortie, '-frames:v', '1', '-q:v', '3', sortie.replace(/\.mp4$/, '.jpg'))
  rmSync(travail, { recursive: true, force: true })
  console.log(`✓ ${sortie} : ${Math.round(duree(sortie))} s, sous-titres et affiche`)
}

function publier() {
  const hote = process.env.MEDIAS_HOTE || 'deploy@51.222.86.37'
  const cible = process.env.MEDIAS_CIBLE || '~/projets/VinaSign/deploy/ovh/medias/'
  execFileSync('ssh', [hote, `mkdir -p ${cible}demo`], { stdio: 'inherit' })
  execFileSync('rsync', ['-av', '--delete', `${join(MEDIAS, 'demo')}/`, `${hote}:${cible}demo/`], { stdio: 'inherit' })
}

const commandes = { voix, tourner, monter, publier }
const [commande] = process.argv.slice(2)
if (!commandes[commande]) { console.error(readFileSync(fileURLToPath(import.meta.url), 'utf8').split('*/')[0]); process.exit(2) }
await commandes[commande]()
