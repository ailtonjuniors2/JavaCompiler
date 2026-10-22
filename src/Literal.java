public class Literal {

    private final int index;
    private final TokenType type;
    private final String value;

    public Literal(int index, TokenType type, String value) {
        this.index = index;
        this.type = type;
        this.value = value;
    }

    public int getIndex() {
        return index;
    }

    public TokenType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Literal{" +
                "index=" + index +
                ", type=" + type +
                ", value='" + value + '\'' +
                '}';
    }
}