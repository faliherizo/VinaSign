package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Un rôle de la plateforme ne s'attribue pas par une organisation. */
public class RoleNonAttribuable extends ErreurMetier {

    public RoleNonAttribuable() {
        super(Nature.INVALIDE, "role-non-attribuable", "Ce rôle ne peut pas être attribué par une organisation.");
    }
}
