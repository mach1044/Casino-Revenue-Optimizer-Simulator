"""Averages consecutive blocks of raw replicate rows (from
MonteCarloDataGenerator's per-replicate output) into one clean row per
table-setup. Usage: python aggregate.py <raw.csv> <replicates> <out.csv>
"""
import csv
import sys

FEATURE_COLS = ["gameType", "countGrinder", "countObnoxious", "countWhale",
                 "countLowStakes", "avgBankroll", "candidateBehaviorStyle",
                 "candidateBankrollStyle", "candidateBalance"]


def main():
    raw_path, replicates, out_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    with open(raw_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(FEATURE_COLS + ["deltaMood", "deltaProfit"])
        for start in range(0, len(rows), replicates):
            block = rows[start:start + replicates]
            mood = sum(float(r["deltaMood"]) for r in block) / len(block)
            profit = sum(float(r["deltaProfit"]) for r in block) / len(block)
            first = block[0]
            writer.writerow([first[c] for c in FEATURE_COLS] + [mood, profit])

    print(f"Wrote {len(rows) // replicates} averaged rows to {out_path}")


if __name__ == "__main__":
    main()
