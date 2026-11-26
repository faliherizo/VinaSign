package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.application.port.EcheancesEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.Enveloppes;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.ModeEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.ReferenceDejaUtilisee;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutSignataire;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Enveloppes et signataires dans PostgreSQL, sous la RLS ; l'expiration passe par ses fonctions de plateforme. */
@Component
class EnveloppesJdbc implements Enveloppes, EcheancesEnveloppes, ca.vinaitsolution.vinasign.enveloppe.application.port.EnveloppesAFinaliser {

    private final NamedParameterJdbcTemplate jdbc;
    private final Chiffrement chiffrement;

    EnveloppesJdbc(NamedParameterJdbcTemplate jdbc, Chiffrement chiffrement) {
        this.jdbc = jdbc;
        this.chiffrement = chiffrement;
    }

    @Override
    public void ajouter(Enveloppe e) {
        try {
            jdbc.update("""
                    INSERT INTO enveloppe (id, tenant_id, client_id, reference_externe, mode, statut, expire_le, cree_le, modifie_le)
                    VALUES (:id, :org, :client, :ref, :mode, :statut, :expire, :cree, :modifie)""", parametres(e)
                    .addValue("client", e.application()).addValue("ref", e.referenceExterne()).addValue("mode", e.mode().code())
                    .addValue("expire", Timestamp.from(e.expireLe())).addValue("cree", Timestamp.from(e.creeLe())));
        } catch (DuplicateKeyException doublon) {
            throw new ReferenceDejaUtilisee();
        }
        ecrireSignataires(e);
    }

    @Override
    public void remplacer(Enveloppe e) {
        jdbc.update("UPDATE enveloppe SET statut = :statut, modifie_le = :modifie WHERE id = :id", parametres(e));
        ecrireSignataires(e);
    }

    @Override
    public Optional<Enveloppe> parId(IdentifiantEnveloppe id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id.valeur());
        List<Signataire> signataires = jdbc.query("SELECT * FROM signataire WHERE enveloppe_id = :id ORDER BY ordre, nom", p,
                (rs, n) -> signataire(rs, chiffrement));
        return jdbc.query("SELECT * FROM enveloppe WHERE id = :id", p, (rs, n) -> enveloppe(rs, signataires)).stream().findFirst();
    }

    @Override
    public long creeesDepuis(IdentifiantOrganisation organisation, Instant debut) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM enveloppe WHERE tenant_id = :org AND cree_le >= :debut",
                new MapSqlParameterSource("org", organisation.valeur()).addValue("debut", Timestamp.from(debut)), Long.class);
        return n == null ? 0 : n;
    }

    /** Sans leurs signataires : l'expiration ne change que le statut de l'enveloppe. */
    @Override
    public List<Enveloppe> echues(Instant maintenant, int limite) {
        return jdbc.query("SELECT * FROM enveloppe_echues(:maintenant, :limite)",
                new MapSqlParameterSource("maintenant", Timestamp.from(maintenant)).addValue("limite", limite), (rs, n) -> enveloppe(rs, List.of()));
    }

    @Override
    public void expirer(Enveloppe expiree, EvenementEnveloppe ev) {
        jdbc.queryForObject("SELECT enveloppe_expirer(:id, :le, :evenement, :type, :acteur)", new MapSqlParameterSource()
                .addValue("id", expiree.id().valeur()).addValue("le", Timestamp.from(ev.survenuLe())).addValue("evenement", ev.id())
                .addValue("type", ev.type()).addValue("acteur", ev.acteur()), Boolean.class);
    }

    private void ecrireSignataires(Enveloppe e) {
        for (Signataire s : e.signataires()) {
            jdbc.update("""
                    INSERT INTO signataire (id, tenant_id, enveloppe_id, nom, courriel, langue, type, ordre, sujet_externe, statut, agi_le,
                                            telephone_chiffre, niveau_exige)
                    VALUES (:id, :org, :enveloppe, :nom, :courriel, :langue, :type, :ordre, :sujet, :statut, :agi, :telephone, :niveau)
                    ON CONFLICT (id) DO UPDATE SET statut = EXCLUDED.statut, agi_le = EXCLUDED.agi_le""", new MapSqlParameterSource()
                    .addValue("id", s.id()).addValue("org", e.organisation().valeur()).addValue("enveloppe", e.id().valeur())
                    .addValue("nom", s.nom()).addValue("courriel", s.courriel()).addValue("langue", s.langue()).addValue("type", s.type())
                    .addValue("ordre", s.ordre()).addValue("sujet", s.sujetExterne()).addValue("statut", s.statut().code())
                    .addValue("agi", s.agiLe() == null ? null : Timestamp.from(s.agiLe()))
                    .addValue("telephone", s.telephone() == null ? null : chiffrement.chiffrer(s.telephone()))
                    .addValue("niveau", s.niveauExige()));
        }
        jdbc.update("DELETE FROM signataire WHERE enveloppe_id = :enveloppe AND NOT (id = ANY(:ids))", new MapSqlParameterSource()
                .addValue("enveloppe", e.id().valeur()).addValue("ids", e.signataires().stream().map(Signataire::id).toArray(UUID[]::new)));
    }

    private static MapSqlParameterSource parametres(Enveloppe e) {
        return new MapSqlParameterSource().addValue("id", e.id().valeur()).addValue("org", e.organisation().valeur())
                .addValue("statut", e.statut().code()).addValue("modifie", Timestamp.from(e.modifieLe()));
    }

    private static Enveloppe enveloppe(ResultSet rs, List<Signataire> signataires) throws SQLException {
        return new Enveloppe(new IdentifiantEnveloppe(rs.getObject("id", UUID.class)),
                new IdentifiantOrganisation(rs.getObject("tenant_id", UUID.class)), rs.getString("client_id"), rs.getString("reference_externe"),
                ModeEnveloppe.depuisCode(rs.getString("mode")), StatutEnveloppe.depuisCode(rs.getString("statut")), signataires,
                rs.getTimestamp("expire_le").toInstant(), rs.getTimestamp("cree_le").toInstant(), rs.getTimestamp("modifie_le").toInstant());
    }

    private static Signataire signataire(ResultSet rs, Chiffrement chiffrement) throws SQLException {
        Timestamp agi = rs.getTimestamp("agi_le");
        byte[] telephone = rs.getBytes("telephone_chiffre");
        return new Signataire(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getString("courriel"), rs.getString("langue"),
                rs.getString("type"), rs.getInt("ordre"), rs.getString("sujet_externe"), StatutSignataire.depuisCode(rs.getString("statut")),
                agi == null ? null : agi.toInstant(), telephone == null ? null : chiffrement.dechiffrer(telephone)).avecNiveauExige(rs.getString("niveau_exige"));
    }

    @Override
    public List<AFinaliser> aFinaliser(int limite) {
        return jdbc.query("SELECT id, tenant_id FROM enveloppes_a_finaliser(:limite)", new MapSqlParameterSource("limite", limite),
                (rs, n) -> new AFinaliser(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class)));
    }
}
