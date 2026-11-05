package fr.oesphy.pyfish.content;

import java.util.List;

public record OreBiomeSelection(Kind kind, List<String> values) {
    public enum Kind {
        OVERWORLD, NETHER, END, TAG, BIOMES
    }

    public OreBiomeSelection {
        values = List.copyOf(values);
    }

    public static OreBiomeSelection dimension(Kind kind) {
        return new OreBiomeSelection(kind, List.of());
    }
}
