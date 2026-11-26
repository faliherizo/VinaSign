package ca.vinaitsolution.vinasign.acces.application.dto;

import java.time.Instant;
import java.util.List;

/** Ce que porte le jeton présenté : de quoi diagnostiquer une intégration sans décoder le jeton soi-même. */
public record VueJeton(String client, String organisation, List<String> portees, Instant expireLe) {
}
