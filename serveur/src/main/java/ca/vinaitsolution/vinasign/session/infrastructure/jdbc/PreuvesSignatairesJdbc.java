package ca.vinaitsolution.vinasign.session.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.application.port.LecturePreuvesSignataires;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Consentements, niveaux atteints et attestations des signataires d'une enveloppe, sous la RLS de son organisation. */
@Component
class PreuvesSignatairesJdbc implements LecturePreuvesSignataires {

    private final NamedParameterJdbcTemplate jdbc;

    PreuvesSignatairesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PreuveSignataire> de(UUID enveloppe) {
        return jdbc.query("""
                SELECT s.id,
                       c.version_texte, c.empreinte_texte, c.accepte_le,
                       (SELECT ss.niveau_atteint FROM session_signataire ss WHERE ss.signataire_id = s.id AND ss.niveau_atteint IS NOT NULL
                        ORDER BY ss.expire_le DESC LIMIT 1) AS niveau,
                       COALESCE((SELECT ARRAY['idv', 'fournisseur:' || v.fournisseur]
                                        || CASE WHEN v.methode IS NULL THEN ARRAY[]::text[] ELSE ARRAY['methode:' || v.methode] END
                                        || CASE WHEN v.assurance IS NULL THEN ARRAY[]::text[] ELSE ARRAY['assurance:' || v.assurance] END
                                 FROM verification_identite v WHERE v.signataire_id = s.id AND v.etat = 'reussie' ORDER BY v.fin DESC LIMIT 1),
                                (SELECT a.methodes FROM attestation_lien a JOIN lien_signature l ON l.id = a.lien_id WHERE l.signataire_id = s.id
                                 ORDER BY a.atteste_le DESC LIMIT 1),
                                (SELECT ARRAY[ss.amr]::text[] FROM session_signataire ss WHERE ss.signataire_id = s.id AND ss.amr IS NOT NULL
                                 ORDER BY ss.expire_le DESC LIMIT 1)) AS methodes
                FROM signataire s
                LEFT JOIN LATERAL (SELECT * FROM consentement c WHERE c.signataire_id = s.id ORDER BY c.accepte_le DESC LIMIT 1) c ON true
                WHERE s.enveloppe_id = :e ORDER BY s.ordre, s.id""", new MapSqlParameterSource("e", enveloppe), (rs, n) -> {
            Timestamp consenti = rs.getTimestamp("accepte_le");
            Array methodes = rs.getArray("methodes");
            return new PreuveSignataire(rs.getObject("id", UUID.class), rs.getString("version_texte"), rs.getString("empreinte_texte"),
                    consenti == null ? null : consenti.toInstant(), rs.getString("niveau"),
                    methodes == null ? List.of() : Arrays.asList((String[]) methodes.getArray()));
        });
    }
}
