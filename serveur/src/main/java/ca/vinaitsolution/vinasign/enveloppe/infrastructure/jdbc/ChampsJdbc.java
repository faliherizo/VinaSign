package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.SignaturesSignataires;
import ca.vinaitsolution.vinasign.enveloppe.application.port.LectureSaisies;
import ca.vinaitsolution.vinasign.enveloppe.domain.SignatureApposee;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Champs, valeurs saisies et trace des signatures dans PostgreSQL, sous la RLS. */
@Component
class ChampsJdbc implements ChampsEnveloppe, SignaturesSignataires, LectureSaisies {

    private final NamedParameterJdbcTemplate jdbc;

    ChampsJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void definir(IdentifiantEnveloppe enveloppe, List<Champ> champs) {
        jdbc.update("DELETE FROM champ WHERE enveloppe_id = :e", new MapSqlParameterSource("e", enveloppe.valeur()));
        for (Champ c : champs) {
            jdbc.update("""
                    INSERT INTO champ (id, tenant_id, enveloppe_id, signataire_id, type, libelle, page, x, y, largeur, hauteur, obligatoire)
                    SELECT :id, tenant_id, id, :s, :type, :libelle, :page, :x, :y, :l, :h, :o FROM enveloppe WHERE id = :e""",
                    new MapSqlParameterSource().addValue("id", c.id()).addValue("e", enveloppe.valeur()).addValue("s", c.signataire())
                            .addValue("type", c.type()).addValue("libelle", c.libelle()).addValue("page", c.page()).addValue("x", c.x())
                            .addValue("y", c.y()).addValue("l", c.largeur()).addValue("h", c.hauteur()).addValue("o", c.obligatoire()));
        }
    }

    @Override
    public List<Champ> de(IdentifiantEnveloppe enveloppe) {
        return jdbc.query("SELECT * FROM champ WHERE enveloppe_id = :e ORDER BY page, y, x", new MapSqlParameterSource("e", enveloppe.valeur()),
                (rs, n) -> new Champ(rs.getObject("id", UUID.class), rs.getObject("signataire_id", UUID.class), rs.getString("type"),
                        rs.getString("libelle"), rs.getInt("page"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("largeur"),
                        rs.getDouble("hauteur"), rs.getBoolean("obligatoire")));
    }

    @Override
    public void enregistrerValeurs(IdentifiantEnveloppe enveloppe, UUID signataire, Map<UUID, String> valeurs) {
        valeurs.forEach((champ, valeur) -> jdbc.update("""
                INSERT INTO valeur_champ (tenant_id, champ_id, signataire_id, valeur) SELECT tenant_id, id, :s, :v FROM champ WHERE id = :c""",
                new MapSqlParameterSource("c", champ).addValue("s", signataire).addValue("v", valeur)));
    }

    @Override
    public void ajouter(UUID id, IdentifiantEnveloppe enveloppe, UUID signataire, String mode, String cleImage, String nomSaisi, Instant le) {
        jdbc.update("""
                INSERT INTO signature_signataire (id, tenant_id, enveloppe_id, signataire_id, mode, cle_image, nom_saisi, signe_le)
                SELECT :id, tenant_id, id, :s, :m, :cle, :nom, :le FROM enveloppe WHERE id = :e""", new MapSqlParameterSource().addValue("id", id)
                .addValue("e", enveloppe.valeur()).addValue("s", signataire).addValue("m", mode).addValue("cle", cleImage).addValue("nom", nomSaisi)
                .addValue("le", Timestamp.from(le)));
    }

    @Override
    public Map<UUID, String> valeurs(IdentifiantEnveloppe enveloppe) {
        Map<UUID, String> valeurs = new java.util.HashMap<>();
        jdbc.query("SELECT v.champ_id, v.valeur FROM valeur_champ v JOIN champ c ON c.id = v.champ_id WHERE c.enveloppe_id = :e",
                new MapSqlParameterSource("e", enveloppe.valeur()), rs -> {
                    valeurs.put(rs.getObject("champ_id", UUID.class), rs.getString("valeur"));
                });
        return Map.copyOf(valeurs);
    }

    @Override
    public List<SignatureApposee> signatures(IdentifiantEnveloppe enveloppe) {
        return jdbc.query("SELECT signataire_id, cle_image, nom_saisi FROM signature_signataire WHERE enveloppe_id = :e ORDER BY signe_le",
                new MapSqlParameterSource("e", enveloppe.valeur()),
                (rs, n) -> new SignatureApposee(rs.getObject("signataire_id", UUID.class), rs.getString("cle_image"), rs.getString("nom_saisi")));
    }
}
