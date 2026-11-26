package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Décodeur des jetons du fournisseur d'identité du portail (type dédié : distinct du décodeur des jetons VinaSign). */
public record DecodeurPortail(JwtDecoder decodeur) {
}
