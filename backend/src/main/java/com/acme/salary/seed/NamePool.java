package com.acme.salary.seed;

import java.util.List;
import java.util.SplittableRandom;

final class NamePool {

    private static final List<String> FIRST_NAMES = List.of(
            "Aarav", "Aisha", "Akira", "Alejandro", "Amelia", "Ana", "Ananya", "Arjun", "Ben", "Carlos",
            "Chloe", "Chen", "Daniel", "Diego", "Elena", "Emma", "Fatima", "Felix", "Gabriel", "Hana",
            "Hannah", "Hiroshi", "Isabella", "Ivan", "James", "Jane", "Julia", "Kai", "Kavya", "Leon",
            "Liam", "Lucas", "Maria", "Mateo", "Mei", "Mia", "Noah", "Olivia", "Omar", "Priya",
            "Rafael", "Ravi", "Rohan", "Sakura", "Sara", "Sofia", "Sophie", "Thomas", "Wei", "Yuki");

    private static final List<String> LAST_NAMES = List.of(
            "Anderson", "Bauer", "Brown", "Chen", "Costa", "Dubois", "Fischer", "Garcia", "Gupta", "Ito",
            "Iyer", "Jansen", "Johnson", "Kim", "Kumar", "Lee", "Lefebvre", "Lim", "Lopez", "Martin",
            "Meyer", "Miller", "Moreau", "Muller", "Nair", "Nakamura", "Oliveira", "Patel", "Reddy", "Rossi",
            "Santos", "Schmidt", "Sharma", "Silva", "Singh", "Smith", "Suzuki", "Tan", "Tanaka", "Taylor",
            "Thomas", "Van Dijk", "Wagner", "Wang", "Watanabe", "Weber", "Williams", "Wilson", "Wong", "Yamamoto");

    private NamePool() {
    }

    static String draw(SplittableRandom random) {
        return FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size())) + " " + LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
    }
}
