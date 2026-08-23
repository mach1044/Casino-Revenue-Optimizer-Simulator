import java.util.List;

/** A bounded summary of the crowd and social fit at a player's table. */
public final class TableEnvironment {
    private final double averageMood;
    private final double compatibility;
    private final double socialScore;

    private TableEnvironment(double averageMood, double compatibility,
                             double socialScore){
        this.averageMood = averageMood;
        this.compatibility = compatibility;
        this.socialScore = socialScore;
    }

    public static TableEnvironment evaluate(Player player, Game game){
        if(game == null){
            return new TableEnvironment(0, 0, 0);
        }
        List<Player> table = game.getCurrentTable();
        double totalMood = 0;
        double totalCompatibility = 0;
        int others = 0;
        for(Player other : table){
            if(other == player){
                continue;
            }
            totalMood += other.getMood();
            totalCompatibility += player.getBehaviorStyle().affinityFor(
                    other.getBehaviorStyle().getType());
            others++;
        }
        double averageMood = others == 0 ? 0 : totalMood / others;
        double compatibility = others == 0 ? 0 : totalCompatibility / others;
        // Compatibility dominates, while table mood provides a smaller
        // shared emotional influence.
        double socialScore = others == 0 ? 0 : clamp(
                0.75 * compatibility + 0.25 * averageMood);
        return new TableEnvironment(averageMood, compatibility, socialScore);
    }

    public double getAverageMood(){ return averageMood; }
    public double getCompatibility(){ return compatibility; }
    public double getSocialScore(){ return socialScore; }

    private static double clamp(double value){
        return Math.max(-1, Math.min(1, value));
    }
}
