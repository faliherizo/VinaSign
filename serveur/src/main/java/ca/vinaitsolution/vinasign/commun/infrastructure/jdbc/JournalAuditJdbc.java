package ca.vinaitsolution.vinasign.commun.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import java.sql.Timestamp;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Journal d'audit dans PostgreSQL : le rôle applicatif peut ajouter, jamais modifier ni supprimer. */
@Component
class JournalAuditJdbc implements JournalAudit {

    private final NamedParameterJdbcTemplate jdbc;

    JournalAuditJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void consigner(EvenementAudit e) {
        jdbc.update("""
                INSERT INTO journal_audit (id, tenant_id, acteur, action, cible, survenu_le)
                VALUES (:id, :organisation, :acteur, :action, :cible, :survenu)""", new MapSqlParameterSource()
                .addValue("id", e.id())
                .addValue("organisation", e.organisation().valeur())
                .addValue("acteur", e.acteur().reference())
                .addValue("action", e.action())
                .addValue("cible", e.cible())
                .addValue("survenu", Timestamp.from(e.survenuLe())));
    }
}
