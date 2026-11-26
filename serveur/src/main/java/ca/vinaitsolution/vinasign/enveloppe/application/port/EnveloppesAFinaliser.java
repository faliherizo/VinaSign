package ca.vinaitsolution.vinasign.enveloppe.application.port;

import java.util.List;
import java.util.UUID;

/** Enveloppes envoyées dont toutes les signatures sont recueillies, toutes organisations confondues (tâche de plateforme). */
public interface EnveloppesAFinaliser {

    record AFinaliser(UUID enveloppe, UUID organisation) {
    }

    List<AFinaliser> aFinaliser(int limite);
}
