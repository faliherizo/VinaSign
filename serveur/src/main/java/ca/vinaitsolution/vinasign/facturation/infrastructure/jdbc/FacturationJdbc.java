package ca.vinaitsolution.vinasign.facturation.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.facturation.application.port.Abonnements;
import ca.vinaitsolution.vinasign.facturation.application.port.EvenementsPaiement;
import ca.vinaitsolution.vinasign.facturation.application.port.Factures;
import ca.vinaitsolution.vinasign.facturation.domain.Abonnement;
import ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement;
import ca.vinaitsolution.vinasign.facturation.domain.Facture;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Abonnements et factures sous la RLS ; événements traités et organisation d'un client par la plateforme (VS-121). */
@Component
class FacturationJdbc implements Abonnements, Factures, EvenementsPaiement {

    private final NamedParameterJdbcTemplate jdbc;

    FacturationJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(Abonnement a) {
        jdbc.update("""
                INSERT INTO abonnement (tenant_id, prestataire, client, reference, plan, etat, modifie_le)
                VALUES (:org, :prestataire, :client, :reference, :plan, :etat, :le)
                ON CONFLICT (tenant_id) DO UPDATE SET client = EXCLUDED.client, reference = EXCLUDED.reference, plan = EXCLUDED.plan,
                  etat = EXCLUDED.etat, modifie_le = EXCLUDED.modifie_le""",
                new MapSqlParameterSource("org", a.organisation()).addValue("prestataire", a.prestataire()).addValue("client", a.client())
                        .addValue("reference", a.reference()).addValue("plan", a.plan()).addValue("etat", a.etat().code())
                        .addValue("le", Timestamp.from(a.modifieLe())));
    }

    @Override
    public Optional<Abonnement> de(UUID organisation) {
        return jdbc.query("SELECT * FROM abonnement WHERE tenant_id = :org", new MapSqlParameterSource("org", organisation),
                (rs, n) -> new Abonnement(rs.getObject("tenant_id", UUID.class), rs.getString("prestataire"), rs.getString("client"),
                        rs.getString("reference"), rs.getString("plan"), EtatAbonnement.deCode(rs.getString("etat")), rs.getTimestamp("modifie_le").toInstant()))
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> organisationDuClient(String client) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT abonnement_organisation(:c)", new MapSqlParameterSource("c", client), UUID.class));
    }

    @Override
    public void ajouter(Facture f) {
        jdbc.update("""
                INSERT INTO facture (id, tenant_id, reference, numero, montant, devise, url_hebergee, url_pdf, debut_periode, fin_periode, payee_le)
                VALUES (:id, :org, :reference, :numero, :montant, :devise, :hebergee, :pdf, :debut, :fin, :payee) ON CONFLICT (reference) DO NOTHING""",
                new MapSqlParameterSource("id", f.id()).addValue("org", f.organisation()).addValue("reference", f.reference()).addValue("numero", f.numero())
                        .addValue("montant", f.montant()).addValue("devise", f.devise()).addValue("hebergee", f.urlHebergee()).addValue("pdf", f.urlPdf())
                        .addValue("debut", horodatage(f.debutPeriode())).addValue("fin", horodatage(f.finPeriode()))
                        .addValue("payee", Timestamp.from(f.payeeLe())));
    }

    @Override
    public List<Facture> deLOrganisation(UUID organisation) {
        return jdbc.query("SELECT * FROM facture WHERE tenant_id = :org ORDER BY payee_le DESC", new MapSqlParameterSource("org", organisation),
                (rs, n) -> new Facture(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("reference"),
                        rs.getString("numero"), rs.getLong("montant"), rs.getString("devise"), rs.getString("url_hebergee"), rs.getString("url_pdf"),
                        instant(rs.getTimestamp("debut_periode")), instant(rs.getTimestamp("fin_periode")), rs.getTimestamp("payee_le").toInstant()));
    }

    @Override
    public boolean retenir(String id, Instant le) {
        return jdbc.update("INSERT INTO evenement_paiement (id, recu_le) VALUES (:id, :le) ON CONFLICT (id) DO NOTHING",
                new MapSqlParameterSource("id", id).addValue("le", Timestamp.from(le))) == 1;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp horodatage(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
