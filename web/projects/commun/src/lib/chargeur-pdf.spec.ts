import { ChargeurPdfJs } from './chargeur-pdf';

/** PDF d'une page A4 portant « Bonjour VinaSign », construit avec une table de références exacte. */
export function pdfMinimal(): ArrayBuffer {
  const contenu = 'BT /F1 24 Tf 72 720 Td (Bonjour VinaSign) Tj ET';
  const objets = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${contenu.length} >>\nstream\n${contenu}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let pdf = '%PDF-1.4\n';
  const positions: number[] = [];
  objets.forEach((o, i) => {
    positions.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objets.length + 1}\n0000000000 65535 f \n`;
  pdf += positions.map((p) => `${String(p).padStart(10, '0')} 00000 n \n`).join('');
  pdf += `trailer\n<< /Size ${objets.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return new TextEncoder().encode(pdf).buffer as ArrayBuffer;
}

describe('ChargeurPdfJs', () => {
  it('ouvre un PDF, en dessine la page à la largeur demandée et en extrait le texte', async () => {
    const doc = await new ChargeurPdfJs().ouvrir(pdfMinimal());
    expect(doc.nombrePages).toBe(1);
    const toile = document.createElement('canvas');
    const rapport = await doc.rendre(1, toile, 595);
    expect(toile.width).toBe(595);
    expect(rapport).toBeCloseTo(842 / 595, 2);
    expect(await doc.texte(1)).toBe('Bonjour VinaSign');
    await doc.fermer();
  });

  it('rejette des octets qui ne sont pas un PDF', async () => {
    await expectAsync(new ChargeurPdfJs().ouvrir(new TextEncoder().encode('pas un pdf').buffer as ArrayBuffer)).toBeRejected();
  });
});
