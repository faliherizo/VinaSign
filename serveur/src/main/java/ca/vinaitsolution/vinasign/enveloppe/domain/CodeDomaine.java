package ca.vinaitsolution.vinasign.enveloppe.domain;

/** Un invariant du domaine désigné, à l'extérieur, par le code de son référentiel (libellés, documentation). */
interface CodeDomaine {

    String code();

    static <E extends Enum<E> & CodeDomaine> E depuis(Class<E> type, String code) {
        for (E e : type.getEnumConstants()) {
            if (e.code().equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Code inconnu pour " + type.getSimpleName() + ".");
    }
}
