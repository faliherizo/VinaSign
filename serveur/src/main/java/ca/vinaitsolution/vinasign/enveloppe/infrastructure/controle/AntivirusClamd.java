package ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle;

import ca.vinaitsolution.vinasign.enveloppe.application.port.AnalyseAntivirus;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse par ClamAV (démon clamd, commande INSTREAM) : le contenu est transmis par blocs, jamais écrit sur disque.
 * Une analyse impossible (démon absent, réponse d'erreur) est une erreur, jamais un document réputé sain.
 */
public class AntivirusClamd implements AnalyseAntivirus {

    private static final int BLOC = 64 * 1024;
    private static final Pattern MENACE = Pattern.compile("stream: (.+) FOUND");

    private final String hote;
    private final int port;
    private final Duration delai;

    public AntivirusClamd(String hote, int port, Duration delai) {
        this.hote = hote;
        this.port = port;
        this.delai = delai;
    }

    @Override
    public Optional<String> analyser(byte[] contenu) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(hote, port), (int) delai.toMillis());
            s.setSoTimeout((int) delai.toMillis());
            DataOutputStream sortie = new DataOutputStream(s.getOutputStream());
            sortie.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < contenu.length; i += BLOC) {
                int n = Math.min(BLOC, contenu.length - i);
                sortie.writeInt(n);
                sortie.write(contenu, i, n);
            }
            sortie.writeInt(0);
            sortie.flush();
            return interpreter(lire(s.getInputStream()));
        } catch (IOException e) {
            throw new UncheckedIOException("Analyse antivirale impossible.", e);
        }
    }

    static Optional<String> interpreter(String reponse) {
        if (reponse.equals("stream: OK")) {
            return Optional.empty();
        }
        Matcher m = MENACE.matcher(reponse);
        if (m.matches()) {
            return Optional.of(m.group(1));
        }
        throw new IllegalStateException("Réponse de l'antivirus inattendue : l'analyse n'a pas abouti.");
    }

    private static String lire(InputStream entree) throws IOException {
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        int c;
        while ((c = entree.read()) > 0) {
            octets.write(c);
        }
        return octets.toString(StandardCharsets.US_ASCII).strip();
    }
}
