package ca.vinaitsolution.vinasign.organisation.infrastructure.dns;

import ca.vinaitsolution.vinasign.organisation.application.port.ResolutionDns;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;

/**
 * Enregistrements TXT par le client DNS du JDK (VS-122). Le serveur interrogé est une configuration ({@code dns://hôte:port},
 * vide : celui du système) ; délai court, une seule nouvelle tentative. Un nom inexistant rend une liste vide ; une panne
 * de résolution aussi, journalisée : le demandeur réessaie.
 */
public class ResolutionDnsJndi implements ResolutionDns {

    private static final org.slf4j.Logger journal = org.slf4j.LoggerFactory.getLogger(ResolutionDnsJndi.class);

    private final String serveur;

    public ResolutionDnsJndi(String serveur) {
        this.serveur = serveur == null ? "" : serveur.strip();
    }

    @Override
    public List<String> textes(String nom) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", serveur.isEmpty() ? "dns:" : serveur);
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        List<String> textes = new ArrayList<>();
        try {
            InitialDirContext dns = new InitialDirContext(env);
            try {
                Attribute txt = dns.getAttributes(nom, new String[] {"TXT"}).get("TXT");
                if (txt != null) {
                    NamingEnumeration<?> valeurs = txt.getAll();
                    while (valeurs.hasMore()) {
                        textes.add(valeurs.next().toString().replace("\"", ""));
                    }
                }
            } finally {
                dns.close();
            }
        } catch (NameNotFoundException e) {
            return List.of();
        } catch (NamingException e) {
            journal.warn("Résolution DNS impossible ({}) ; la preuve de domaine sera retentée.", e.getClass().getSimpleName());
            return List.of();
        }
        return textes;
    }
}
