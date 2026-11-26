package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ContenuSignatureTest {

    private static byte[] bourre(byte[] objet, int zeros) {
        return Arrays.copyOf(objet, objet.length + zeros);
    }

    @Test
    void lesZerosQuiSuiventLObjetSontRetires() {
        byte[] court = {0x30, 0x03, 0x02, 0x01, 0x05};
        assertArrayEquals(court, ContenuSignature.sansBourrage(bourre(court, 20)));
        byte[] longueurLongue = new byte[4 + 300];
        longueurLongue[0] = 0x30;
        longueurLongue[1] = (byte) 0x82;
        longueurLongue[2] = 0x01;
        longueurLongue[3] = 0x2C;
        Arrays.fill(longueurLongue, 4, longueurLongue.length, (byte) 7);
        assertArrayEquals(longueurLongue, ContenuSignature.sansBourrage(bourre(longueurLongue, 1000)), "longueur sur deux octets (300)");
    }

    @Test
    void unObjetDeLongueurIndefinieEstLuJusquASaFin() throws Exception {
        byte[] ber = new org.bouncycastle.asn1.BERSequence(new org.bouncycastle.asn1.ASN1Integer(5)).getEncoded();
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0x80, ber[1], "longueur indéfinie, comme le CMS de Bouncy Castle");
        byte[] lu = ContenuSignature.sansBourrage(bourre(ber, 64));
        org.junit.jupiter.api.Assertions.assertEquals(org.bouncycastle.asn1.ASN1Primitive.fromByteArray(ber), org.bouncycastle.asn1.ASN1Primitive.fromByteArray(lu));
        org.junit.jupiter.api.Assertions.assertThrows(java.io.UncheckedIOException.class,
                () -> ContenuSignature.sansBourrage(new byte[] {0x30, (byte) 0x80, 0x02, 0x05}));
    }

    @Test
    void unContenuSansBourrageOuIllisibleEstRenduTelQuel() {
        byte[] exact = {0x30, 0x03, 0x02, 0x01, 0x05};
        assertSame(exact, ContenuSignature.sansBourrage(exact));
        byte[] tropLong = {0x30, (byte) 0x85, 1, 2, 3, 4, 5};
        assertSame(tropLong, ContenuSignature.sansBourrage(tropLong));
        byte[] tronque = {0x30, (byte) 0x82, 0x01};
        assertSame(tronque, ContenuSignature.sansBourrage(tronque));
        byte[] unOctet = {0x30};
        assertSame(unOctet, ContenuSignature.sansBourrage(unOctet));
        assertNull(ContenuSignature.sansBourrage(null));
    }
}
