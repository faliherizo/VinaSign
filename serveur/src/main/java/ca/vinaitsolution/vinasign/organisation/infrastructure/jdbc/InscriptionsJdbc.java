package ca.vinaitsolution.vinasign.organisation.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.organisation.application.port.Inscriptions;
import ca.vinaitsolution.vinasign.organisation.domain.Inscription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Demandes d'inscription dans PostgreSQL : table de la plateforme, hors RLS (aucune organisation n'existe encore). */
@Component
class InscriptionsJdbc implements Inscriptions {

    private final NamedParameterJdbcTemplate jdbc;

    InscriptionsJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(Inscription i) {
        jdbc.update("""
                INSERT INTO inscription (id, nom_organisation, courriel, domaine, langue, etat, empreinte_jeton, jeton_expire_le, defi_dns, motif_refus,
                                         cree_le, modifie_le)
                VALUES (:id, :nom, :courriel, :domaine, :langue, :etat, :empreinte, :expire, :defi, :motif, :cree, :modifie)""", parametres(i));
    }

    @Override
    public void remplacer(Inscription i) {
        jdbc.update("UPDATE inscription SET etat = :etat, motif_refus = :motif, modifie_le = :modifie WHERE id = :id", parametres(i));
    }

    @Override
    public Optional<Inscription> parId(UUID id) {
        return une("WHERE id = :v", id);
    }

    @Override
    public Optional<Inscription> parEmpreinteJeton(String empreinte) {
        return une("WHERE empreinte_jeton = :v", empreinte);
    }

    @Override
    public Optional<Inscription> ouverteDe(String courriel) {
        return une("WHERE lower(courriel) = lower(:v) AND etat NOT IN ('acceptee', 'refusee')", courriel);
    }

    @Override
    public List<Inscription> aValider() {
        return jdbc.query("SELECT * FROM inscription WHERE etat = 'a-valider' ORDER BY modifie_le", new MapSqlParameterSource(), (rs, n) -> lire(rs));
    }

    private Optional<Inscription> une(String filtre, Object valeur) {
        return jdbc.query("SELECT * FROM inscription " + filtre, new MapSqlParameterSource("v", valeur), (rs, n) -> lire(rs)).stream().findFirst();
    }

    private static MapSqlParameterSource parametres(Inscription i) {
        return new MapSqlParameterSource("id", i.id()).addValue("nom", i.nomOrganisation()).addValue("courriel", i.courriel())
                .addValue("domaine", i.domaine()).addValue("langue", i.langue()).addValue("etat", i.etat().code())
                .addValue("empreinte", i.empreinteJeton()).addValue("expire", Timestamp.from(i.jetonExpireLe())).addValue("defi", i.defiDns())
                .addValue("motif", i.motifRefus()).addValue("cree", Timestamp.from(i.creeLe())).addValue("modifie", Timestamp.from(i.modifieLe()));
    }

    private static Inscription lire(ResultSet rs) throws SQLException {
        return new Inscription(rs.getObject("id", UUID.class), rs.getString("nom_organisation"), rs.getString("courriel"), rs.getString("domaine"),
                rs.getString("langue"), Inscription.Etat.deCode(rs.getString("etat")), rs.getString("empreinte_jeton"),
                rs.getTimestamp("jeton_expire_le").toInstant(), rs.getString("defi_dns"), rs.getString("motif_refus"), rs.getTimestamp("cree_le").toInstant(),
                rs.getTimestamp("modifie_le").toInstant());
    }
}
