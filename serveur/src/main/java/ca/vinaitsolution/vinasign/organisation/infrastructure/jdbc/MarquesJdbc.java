package ca.vinaitsolution.vinasign.organisation.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.application.port.LectureMarque;
import ca.vinaitsolution.vinasign.organisation.application.port.Marques;
import ca.vinaitsolution.vinasign.organisation.domain.Marque;
import ca.vinaitsolution.vinasign.organisation.domain.SousDomainePris;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Marques dans PostgreSQL, sous la RLS de l'organisation ; lues aussi pour l'interface du signataire (même organisation). */
@Component
class MarquesJdbc implements Marques, LectureMarque {

    private final NamedParameterJdbcTemplate jdbc;

    MarquesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(UUID organisation, Marque m, Instant le) {
        try {
            jdbc.update("""
                    INSERT INTO marque_organisation (tenant_id, nom_affiche, couleur_primaire, logo, sous_domaine, modifie_le)
                    VALUES (:org, :nom, :couleur, :logo, :sd, :le)
                    ON CONFLICT (tenant_id) DO UPDATE SET nom_affiche = EXCLUDED.nom_affiche, couleur_primaire = EXCLUDED.couleur_primaire,
                      logo = EXCLUDED.logo, sous_domaine = EXCLUDED.sous_domaine, modifie_le = EXCLUDED.modifie_le""",
                    new MapSqlParameterSource("org", organisation).addValue("nom", m.nomAffiche()).addValue("couleur", m.couleurPrimaire())
                            .addValue("logo", m.logo()).addValue("sd", m.sousDomaine()).addValue("le", Timestamp.from(le)));
        } catch (DuplicateKeyException e) {
            throw new SousDomainePris();
        }
    }

    @Override
    public Optional<Marque> de(UUID organisation) {
        return jdbc.query("SELECT * FROM marque_organisation WHERE tenant_id = :org", new MapSqlParameterSource("org", organisation),
                (rs, n) -> new Marque(rs.getString("nom_affiche"), rs.getString("couleur_primaire"), rs.getBytes("logo"), rs.getString("sous_domaine")))
                .stream().findFirst();
    }

    @Override
    public Optional<VueMarque> deOrganisation(UUID organisation) {
        return de(organisation).map(m -> new VueMarque(m.nomAffiche(), m.couleurPrimaire(),
                m.logo() == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(m.logo())));
    }
}
