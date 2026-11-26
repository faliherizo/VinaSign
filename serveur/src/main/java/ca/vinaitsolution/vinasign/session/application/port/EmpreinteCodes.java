package ca.vinaitsolution.vinasign.session.application.port;

import java.util.UUID;

/** Empreinte d'un code à usage unique, à clé secrète (HMAC) : un code à six chiffres ne se protège pas par un simple hachage. */
public interface EmpreinteCodes {

    String empreinte(UUID defi, String code);
}
