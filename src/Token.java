public class Token {

    private final TokenType type;
    private final String value;
    private final int line;
    private final int column;
    private final int tableIndex;

    public Token(
            TokenType type,
            String value,
            int line,
            int column,
            int tableIndex
    ) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.tableIndex = tableIndex;
    }

    public TokenType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getTableIndex() {
        return tableIndex;
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", value='" + value + '\'' +
                ", line=" + line +
                ", column=" + column +
                ", tableIndex=" + tableIndex +
                '}';
    }
}