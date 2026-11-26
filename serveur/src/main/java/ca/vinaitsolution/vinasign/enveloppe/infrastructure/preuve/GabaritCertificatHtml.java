package ca.vinaitsolution.vinasign.enveloppe.infrastructure.preuve;

import ca.vinaitsolution.vinasign.enveloppe.application.dto.Certificat;
import ca.vinaitsolution.vinasign.enveloppe.application.port.RenduCertificat;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Gabarit HTML du certificat (VS-072), autonome (aucune ressource externe : la conversion n'a pas d'accès réseau) ; toute
 * valeur venue d'une saisie est échappée. Titres et tableaux à en-têtes : le PDF/A qui en résulte reste lisible et
 * structuré.
 */
@Component
class GabaritCertificatHtml implements RenduCertificat {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private static final String STYLE = "body{font-family:sans-serif;font-size:10pt;color:#111}h1{font-size:16pt}h2{font-size:12pt;margin-top:14pt}"
            + "table{border-collapse:collapse;width:100%}th,td{border:1px solid #777;padding:3pt;text-align:left;vertical-align:top}"
            + "th{background:#eee}.code{font-family:monospace;font-size:8pt;word-break:break-all}.alerte{color:#a4001d;font-weight:bold}";

    @Override
    public byte[] html(Certificat c) {
        StringBuilder h = new StringBuilder("<!DOCTYPE html><html lang=\"").append(e(c.langue())).append("\"><head><meta charset=\"utf-8\"><title>")
                .append(t(c, "titre")).append(" — ").append(e(c.reference())).append("</title><style>").append(STYLE).append("</style></head><body>");
        h.append("<h1>").append(t(c, "titre")).append("</h1><p>").append(t(c, "introduction")).append("</p>");
        h.append("<h2>").append(t(c, "enveloppe")).append("</h2><table>");
        ligne(h, t(c, "reference"), e(c.reference()));
        ligne(h, t(c, "enveloppe"), "<span class=\"code\">" + e(c.identifiant()) + "</span>");
        ligne(h, t(c, "statut"), e(c.statut()));
        ligne(h, t(c, "cree-le"), heure(c, c.creeLe()));
        h.append("</table>");
        documents(h, c);
        signataires(h, c);
        chronologie(h, c);
        integrite(h, c);
        h.append("<p>").append(t(c, "cachet")).append("</p><p>").append(t(c, "genere-le")).append(" ").append(heure(c, c.genereLe()))
                .append("</p></body></html>");
        return h.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void documents(StringBuilder h, Certificat c) {
        h.append("<h2>").append(t(c, "documents")).append("</h2>");
        entete(h, t(c, "nature"), t(c, "empreinte"));
        c.documents().forEach(d -> h.append("<tr><td>").append(e(d.nature())).append("</td><td class=\"code\">").append(e(d.sha256())).append("</td></tr>"));
        h.append("</table>");
    }

    private static void signataires(StringBuilder h, Certificat c) {
        h.append("<h2>").append(t(c, "signataires")).append("</h2>");
        entete(h, t(c, "ordre"), t(c, "nom"), t(c, "statut"), t(c, "signe-le"), t(c, "consentement"), t(c, "niveau"));
        for (Certificat.Signataire s : c.signataires()) {
            String consentement = s.consentement() == null ? t(c, "aucun")
                    : "<span class=\"code\">" + e(s.consentement()) + "</span><br>" + heure(c, s.consentiLe());
            String niveau = s.niveau() == null ? t(c, "aucun") : e(s.niveau()) + (s.methodes().isEmpty() ? "" : " (amr : " + e(String.join(", ", s.methodes())) + ")");
            h.append("<tr><td>").append(s.ordre()).append("</td><td>").append(e(s.nom())).append("</td><td>").append(e(s.statut())).append("</td><td>")
                    .append(heure(c, s.agiLe())).append("</td><td>").append(consentement).append("</td><td>").append(niveau).append("</td></tr>");
        }
        h.append("</table>");
    }

    private static void chronologie(StringBuilder h, Certificat c) {
        h.append("<h2>").append(t(c, "chronologie")).append("</h2>");
        entete(h, t(c, "le"), t(c, "evenement"), t(c, "acteur"));
        c.chronologie().forEach(ev -> h.append("<tr><td>").append(heure(c, ev.le())).append("</td><td>").append(e(ev.libelle())).append("</td><td>")
                .append(e(ev.acteur())).append("</td></tr>"));
        h.append("</table>");
    }

    private static void integrite(StringBuilder h, Certificat c) {
        Certificat.Integrite i = c.integrite();
        h.append("<h2>").append(t(c, "integrite")).append("</h2><p").append(i.integre() ? ">" + t(c, "integre") : " class=\"alerte\">" + t(c, "altere"))
                .append(" (").append(i.evenements()).append(")</p><p>").append(t(c, "ancrage")).append(" : ")
                .append(i.ancrage() == null ? t(c, "non-ancre") : "<span class=\"code\">" + e(i.ancrage()) + "</span> — " + heure(c, i.ancreLe()))
                .append("</p>");
    }

    private static void ligne(StringBuilder h, String entete, String valeur) {
        h.append("<tr><th scope=\"row\">").append(entete).append("</th><td>").append(valeur).append("</td></tr>");
    }

    private static void entete(StringBuilder h, String... colonnes) {
        h.append("<table><tr>");
        for (String col : List.of(colonnes)) {
            h.append("<th scope=\"col\">").append(col).append("</th>");
        }
        h.append("</tr>");
    }

    private static String heure(Certificat c, Instant le) {
        return le == null ? t(c, "aucun") : HEURE.format(le);
    }

    /** Texte fixe du certificat (référentiel) ; le code s'il manque, ce qui le rend visible à la recette. */
    private static String t(Certificat c, String code) {
        return e(c.textes().getOrDefault(code, code));
    }

    static String e(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }
}
