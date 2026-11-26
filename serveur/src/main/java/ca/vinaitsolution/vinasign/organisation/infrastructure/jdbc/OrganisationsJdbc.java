package ca.vinaitsolution.vinasign.organisation.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.application.port.PlanOrganisations;
import ca.vinaitsolution.vinasign.commun.application.port.StatutOrganisations;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import ca.vinaitsolution.vinasign.organisation.domain.StatutOrganisation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Organisations dans PostgreSQL. La table est celle de la plateforme (une ligne par organisation), lue par
 * l'administration de la plateforme et par le contrôle d'activité ; elle ne porte pas de données d'une organisation.
 */
@Component
class OrganisationsJdbc implements Organisations, StatutOrganisations, PlanOrganisations {

    private final NamedParameterJdbcTemplate jdbc;

    OrganisationsJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(Organisation o) {
        jdbc.update("""
                INSERT INTO organisation (id, nom, statut, plan, cree_le, modifie_le)
                VALUES (:id, :nom, :statut, :plan, :cree, :modifie)""", parametres(o));
    }

    @Override
    public void remplacer(Organisation o) {
        int lignes = jdbc.update("UPDATE organisation SET nom = :nom, statut = :statut, plan = :plan, modifie_le = :modifie WHERE id = :id",
                parametres(o));
        if (lignes == 0) {
            throw new OrganisationIntrouvable();
        }
    }

    @Override
    public Optional<Organisation> parId(IdentifiantOrganisation id) {
        return jdbc.query("SELECT * FROM organisation WHERE id = :id", new MapSqlParameterSource("id", id.valeur()),
                (rs, n) -> organisation(rs)).stream().findFirst();
    }

    @Override
    public boolean estBloquee(IdentifiantOrganisation id) {
        Boolean active = jdbc.query("SELECT statut = 'active' AS active FROM organisation WHERE id = :id",
                new MapSqlParameterSource("id", id.valeur()), rs -> rs.next() ? rs.getBoolean("active") : Boolean.FALSE);
        return !Boolean.TRUE.equals(active);
    }

    @Override
    public Optional<String> plan(IdentifiantOrganisation id) {
        return jdbc.queryForList("SELECT plan FROM organisation WHERE id = :id", new MapSqlParameterSource("id", id.valeur()), String.class)
                .stream().findFirst();
    }

    private static MapSqlParameterSource parametres(Organisation o) {
        return new MapSqlParameterSource()
                .addValue("id", o.id().valeur())
                .addValue("nom", o.nom())
                .addValue("statut", o.statut().code())
                .addValue("plan", o.plan())
                .addValue("cree", Timestamp.from(o.creeLe()))
                .addValue("modifie", Timestamp.from(o.modifieLe()));
    }

    private static Organisation organisation(ResultSet rs) throws SQLException {
        return new Organisation(new IdentifiantOrganisation(rs.getObject("id", UUID.class)), rs.getString("nom"),
                StatutOrganisation.depuisCode(rs.getString("statut")), rs.getTimestamp("cree_le").toInstant(),
                rs.getTimestamp("modifie_le").toInstant(), rs.getString("plan"));
    }
}
