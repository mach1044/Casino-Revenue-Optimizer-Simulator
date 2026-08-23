"""Regenerates players-input.csv with a left-skewed bankroll distribution:
more low-rollers than even-stevens than high-rollers, and within each tier,
lower balances are more likely than higher ones (a right-skewed draw within
the tier's range, via a power transform on a uniform sample).

Run from BatchInput/:  python generate_players.py
"""
import csv
import random

SEED = 12345
PLAYER_COUNT = 500

# Share of the population in each bankroll tier -- left-skewed toward LOW_ROLLER.
# LOW_ROLLER:EVEN_STEVEN keeps its prior ~50:35 ratio, rescaled to fill the
# remainder after shrinking HIGH_ROLLER to 8%.
TIER_WEIGHTS = [
    ("LOW_ROLLER", 54),
    ("EVEN_STEVEN", 38),
    ("HIGH_ROLLER", 8),
]

# (low, high) dollar range per tier -- original ranges scaled 1.5x, with
# HIGH_ROLLER's upper end set to an explicit 25k cap instead of 1.5x'd.
BALANCE_RANGES = {
    "LOW_ROLLER": (150, 600),
    "EVEN_STEVEN": (450, 1_800),
    "HIGH_ROLLER": (3_000, 25_000),
}

# Higher = more mass pushed toward the low end of each tier's range.
BALANCE_SKEW = 2.5

# Behavior-style mix per tier, matching CasinoBatchRunner.chooseBehaviorStyle.
BEHAVIOR_WEIGHTS = {
    "HIGH_ROLLER": [("WHALE", 33), ("GRINDER", 36), ("OBNOXIOUS", 31)],
    "EVEN_STEVEN": [("GRINDER", 50), ("LOW_STAKES", 25), ("OBNOXIOUS", 25)],
    "LOW_ROLLER": [("LOW_STAKES", 70), ("OBNOXIOUS", 30)],
}

# Default poker traits per behavior style (PLAYER_TRAITS_AND_FORMULAS.md section 3.2).
POKER_DEFAULTS = {
    "WHALE": (0.85, 0.95, 0.50),
    "GRINDER": (0.55, 0.50, 0.75),
    "LOW_STAKES": (0.25, 0.15, 0.40),
    "OBNOXIOUS": (0.20, 0.45, 0.30),
}

MOOD_RANGE = (-0.25, 0.50)
BLACKLISTED_PERCENT = 2.0


def choose_weighted(pairs, rng):
    total = sum(weight for _, weight in pairs)
    roll = rng.uniform(0, total)
    upto = 0
    for value, weight in pairs:
        upto += weight
        if roll <= upto:
            return value
    return pairs[-1][0]


def skewed_balance(low, high, skew, rng):
    u = rng.random()
    return int(round(low + (high - low) * (u ** skew)))


def jitter(base, rng):
    return max(0.0, min(1.0, base + rng.uniform(-0.05, 0.05)))


def main():
    rng = random.Random(SEED)
    rows = []
    for i in range(1, PLAYER_COUNT + 1):
        tier = choose_weighted(TIER_WEIGHTS, rng)
        low, high = BALANCE_RANGES[tier]
        balance = skewed_balance(low, high, BALANCE_SKEW, rng)
        behavior = choose_weighted(BEHAVIOR_WEIGHTS[tier], rng)
        mood = round(rng.uniform(*MOOD_RANGE), 2)
        blacklisted = rng.random() * 100.0 < BLACKLISTED_PERCENT
        participation, aggression, skill = POKER_DEFAULTS[behavior]
        rows.append([
            f"Player-{i:04d}", balance, tier, behavior, mood,
            "true" if blacklisted else "false",
            round(jitter(participation, rng), 2),
            round(jitter(aggression, rng), 2),
            round(jitter(skill, rng), 2),
        ])

    with open("players-input.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["name", "balance", "bankrollType", "behaviorStyle", "mood",
                          "blacklisted", "pokerParticipation", "pokerAggression", "pokerSkill"])
        writer.writerows(rows)

    print(f"Wrote {len(rows)} players to players-input.csv")


if __name__ == "__main__":
    main()
