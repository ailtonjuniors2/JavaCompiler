public class Symbol {

    private final int index;
    private final String name;
    private final TokenType type;

    public Symbol(int index, String name, TokenType type) {
        this.index = index;
        this.name = name;
        this.type = type;
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public TokenType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Symbol{" +
                "index=" + index +
                ", name='" + name + '\'' +
                ", type=" + type +
                '}';
    }
}