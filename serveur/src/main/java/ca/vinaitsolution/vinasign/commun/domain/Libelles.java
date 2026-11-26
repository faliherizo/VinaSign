package ca.vinaitsolution.vinasign.commun.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Libellés d'un élément, par langue. La traduction affichée retombe sur une langue de repli, puis sur le code. */
public final class Libelles {

    private final Map<CodeLangue, String> parLangue;

    private Libelles(Map<CodeLangue, String> parLangue) {
        this.parLangue = Collections.unmodifiableMap(parLangue);
    }

    public static Libelles de(Map<String, String> brut) {
        Map<CodeLangue, String> propre = new LinkedHashMap<>();
        if (brut != null) {
            brut.forEach((langue, texte) -> {
                if (texte != null && !texte.isBlank()) {
                    propre.put(new CodeLangue(langue), texte.strip());
                }
            });
        }
        return new Libelles(propre);
    }

    /** Libellé dans la langue demandée, sinon dans la langue de repli, sinon le code lui-même. */
    public String pour(CodeLangue langue, CodeLangue repli, String code) {
        return Optional.ofNullable(parLangue.get(langue)).or(() -> Optional.ofNullable(parLangue.get(repli))).orElse(code);
    }

    public Map<CodeLangue, String> parLangue() {
        return parLangue;
    }
}
