/** Immutable, independent heuristic-poker characteristics. */
public final class PokerTraits {
    private final double participation;
    private final double aggression;
    private final double skill;

    public PokerTraits(double participation, double aggression, double skill){
        this.participation = validate(participation, "participation");
        this.aggression = validate(aggression, "aggression");
        this.skill = validate(skill, "skill");
    }

    public double getParticipation(){ return participation; }
    public double getAggression(){ return aggression; }
    public double getSkill(){ return skill; }

    public PokerTraits withParticipation(double participation){
        return new PokerTraits(participation, aggression, skill);
    }

    public PokerTraits withAggression(double aggression){
        return new PokerTraits(participation, aggression, skill);
    }

    public PokerTraits withSkill(double skill){
        return new PokerTraits(participation, aggression, skill);
    }

    private static double validate(double value, String name){
        if(Double.isNaN(value) || value < 0 || value > 1){
            throw new IllegalArgumentException("Poker " + name + " must be between 0 and 1");
        }
        return value;
    }
}
