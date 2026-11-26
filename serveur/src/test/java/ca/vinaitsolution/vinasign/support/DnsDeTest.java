package ca.vinaitsolution.vinasign.support;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur DNS minimal sur UDP local, pour éprouver la preuve de domaine (VS-122) sans réseau : il rend les enregistrements
 * TXT déclarés par le test, et NXDOMAIN pour tout autre nom.
 */
public final class DnsDeTest implements AutoCloseable {

    private final DatagramSocket socket;
    private final Map<String, String> textes = new ConcurrentHashMap<>();
    private final Thread service;

    public DnsDeTest() throws SocketException {
        socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        service = new Thread(this::servir, "dns-de-test");
        service.setDaemon(true);
        service.start();
    }

    /** Adresse à configurer : {@code dns://127.0.0.1:<port>}. */
    public String adresse() {
        return "dns://127.0.0.1:" + socket.getLocalPort();
    }

    /** Texte vide : le nom existe sans enregistrement TXT. */
    public void publier(String nom, String texte) {
        textes.put(nom.toLowerCase(), texte);
    }

    private void servir() {
        byte[] tampon = new byte[512];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(tampon, tampon.length);
                socket.receive(p);
                byte[] reponse = repondre(java.util.Arrays.copyOf(p.getData(), p.getLength()));
                socket.send(new DatagramPacket(reponse, reponse.length, p.getSocketAddress()));
            } catch (java.io.IOException e) {
                return;
            }
        }
    }

    private byte[] repondre(byte[] q) {
        int i = 12;
        StringBuilder nom = new StringBuilder();
        while (q[i] != 0) {
            int n = q[i];
            nom.append(nom.length() == 0 ? "" : ".").append(new String(q, i + 1, n, StandardCharsets.US_ASCII));
            i += n + 1;
        }
        int finQuestion = i + 5;
        String texte = textes.get(nom.toString().toLowerCase());
        ByteArrayOutputStream r = new ByteArrayOutputStream();
        r.write(q[0]);
        r.write(q[1]);
        r.write(0x81);
        r.write(texte == null ? 0x83 : 0x80);
        boolean reponse = texte != null && !texte.isEmpty();
        r.writeBytes(new byte[] {0, 1, 0, (byte) (reponse ? 1 : 0), 0, 0, 0, 0});
        r.write(q, 12, finQuestion - 12);
        if (reponse) {
            byte[] t = texte.getBytes(StandardCharsets.US_ASCII);
            r.writeBytes(new byte[] {(byte) 0xC0, 12, 0, 16, 0, 1, 0, 0, 0, 60, 0, (byte) (t.length + 1), (byte) t.length});
            r.writeBytes(t);
        }
        return r.toByteArray();
    }

    @Override
    public void close() {
        socket.close();
    }
}
