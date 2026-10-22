import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Lexer {

    // Código-fonte que será analisado.
    private final String input;
    // Índice do caractere que está sendo lido.
    private int currentPosition;
    // Número da linha atual do código-fonte.
    private int line;
    // Número da coluna atual do código-fonte.
    private int column;

    // Tabela usada para registrar identificadores e literais.
    private final SymbolTable symbolTable;

    // Lista que recebe os tokens reconhecidos.
    private final List<Token> tokens = new ArrayList<>();

    // Conjunto de palavras reservadas da linguagem Java.
    private static final Set<String> KEYWORDS = new HashSet<>(
            Arrays.asList(
                    "abstract", "assert", "boolean", "break",
                    "byte", "case", "catch", "char", "class",
                    "const", "continue", "default", "do",
                    "double", "else", "enum", "extends",
                    "final", "finally", "float", "for",
                    "goto", "if", "implements", "import",
                    "instanceof", "int", "interface", "long",
                    "native", "new", "package", "private",
                    "protected", "public", "return", "short",
                    "static", "strictfp", "super", "switch",
                    "synchronized", "this", "throw", "throws",
                    "transient", "try", "void", "volatile",
                    "while", "_"
            )
    );

    // Operadores; os mais longos aparecem primeiro para evitar reconhecer apenas parte deles.
    private static final String[] OPERATORS = {
            ">>>=", "<<=", ">>=", "...",
            ">>>", "++", "--", "==", "!=",
            "<=", ">=", "&&", "||", "+=",
            "-=", "*=", "/=", "%=", "&=",
            "|=", "^=", "<<", ">>", "->",
            "::", "+", "-", "*", "/", "%",
            "=", "<", ">", "!", "&", "|",
            "^", "~", "?", ":"
    };

    // Inicializa o analisador com o código e prepara as posições e a tabela.
    public Lexer(String input) {
        this.input = input;
        this.currentPosition = 0;
        this.line = 1;
        this.column = 1;
        this.symbolTable = new SymbolTable();
    }

    // Percorre o código-fonte, identifica os elementos e retorna os tokens.
    public List<Token> tokenize() {

        while (!isAtEnd()) {

            char current = peek();

            if (Character.isWhitespace(current)) {
                advance();
                continue;
            }

            int startLine = line;
            int startColumn = column;

            if (current == '/' && peekNext() == '/') {
                readLineComment(startLine, startColumn);
                continue;
            }

            if (current == '/' && peekNext() == '*') {
                readBlockComment(startLine, startColumn);
                continue;
            }

            if (current == '"') {
                readString(startLine, startColumn);
                continue;
            }

            if (current == '\'') {
                readCharacter(startLine, startColumn);
                continue;
            }

            if (Character.isDigit(current)) {
                readNumber(startLine, startColumn);
                continue;
            }

            if (Character.isJavaIdentifierStart(current)) {
                readIdentifier(startLine, startColumn);
                continue;
            }

            if (readOperatorOrDelimiter(startLine, startColumn)) {
                continue;
            }

            throw new RuntimeException(
                    "Caractere desconhecido '" + current +
                            "' na linha " + line +
                            ", coluna " + column
            );
        }

        return new ArrayList<>(tokens);
    }

    // Lê uma palavra e decide se é palavra-chave, literal especial ou identificador.
    private void readIdentifier(int startLine, int startColumn) {

        int start = currentPosition;

        advance();

        while (!isAtEnd()
                && Character.isJavaIdentifierPart(peek())) {
            advance();
        }

        String value = input.substring(start, currentPosition);

        if (KEYWORDS.contains(value)) {

            addToken(
                    TokenType.PALAVRA_CHAVE,
                    value,
                    startLine,
                    startColumn,
                    -1
            );

        } else if (value.equals("true") || value.equals("false")) {

            addLiteralToken(
                    value,
                    TokenType.BOOLEANO,
                    startLine,
                    startColumn
            );

        } else if (value.equals("null")) {

            addLiteralToken(
                    value,
                    TokenType.NULO,
                    startLine,
                    startColumn
            );

        } else {

            int index = symbolTable.addSymbol(
                    value,
                    TokenType.IDENTIFICADOR
            );

            addToken(
                    TokenType.IDENTIFICADOR,
                    value,
                    startLine,
                    startColumn,
                    index
            );
        }
    }

    // Lê um literal numérico e verifica se seu formato é aceito.
    private void readNumber(int startLine, int startColumn) {

        int start = currentPosition;

        while (!isAtEnd() &&
                Character.isLetterOrDigit(peek())) {
            advance();
        }

        while (!isAtEnd() &&
                (peek() == '_' || peek() == '.')) {

            if (peek() == '.' &&
                    peekNext() == '.') {
                break;
            }

            advance();

            while (!isAtEnd() &&
                    (Character.isLetterOrDigit(peek())
                            || peek() == '_')) {
                advance();
            }
        }

        String value = input.substring(start, currentPosition);

        if (!isValidNumber(value)) {
            throw new RuntimeException(
                    "Literal numérico inválido: " + value
            );
        }

        addLiteralToken(
                value,
                TokenType.NUMERAL,
                startLine,
                startColumn
        );
    }

    // Valida formatos numéricos usando uma expressão regular.
    private boolean isValidNumber(String value) {

        // Validação prática dos formatos numéricos Java:
        // decimal, hexadecimal, binário, octal e ponto flutuante.
        return value.matches(
                "(?i)(?:" +
                        "0[x][0-9a-f](?:[0-9a-f_]*[0-9a-f])?[l]?" +
                        "|0[b][01](?:[01_]*[01])[l]?" +
                        "|0[0-7_]*[0-7][l]?" +
                        "|[0-9](?:[0-9_]*[0-9])?[l]?" +
                        "|(?:[0-9](?:[0-9_]*[0-9])?\\.)" +
                        "[0-9_]*(?:[e][+-]?[0-9](?:[0-9_]*[0-9])?)?[fd]?" +
                        "|\\.[0-9](?:[0-9_]*[0-9])?" +
                        "(?:[e][+-]?[0-9](?:[0-9_]*[0-9])?)?[fd]?" +
                        "|[0-9](?:[0-9_]*[0-9])?[e][+-]?" +
                        "[0-9](?:[0-9_]*[0-9])?[fd]?" +
                        "|[0-9](?:[0-9_]*[0-9])?[fd]" +
                        ")"
        );
    }

    // Lê uma string, tratando escapes e verificando se as aspas foram fechadas.
    private void readString(int startLine, int startColumn) {

        int start = currentPosition;

        advance(); // Aspas iniciais.

        while (!isAtEnd() && peek() != '"') {

            if (peek() == '\n' || peek() == '\r') {
                throw new RuntimeException(
                        "String não terminada na linha " + startLine
                );
            }

            if (peek() == '\\') {
                advance();

                if (isAtEnd()) {
                    break;
                }

                advance();
            } else {
                advance();
            }
        }

        if (isAtEnd()) {
            throw new RuntimeException(
                    "String não terminada na linha " + startLine
            );
        }

        advance(); // Aspas finais.

        String value = input.substring(start, currentPosition);

        addLiteralToken(
                value,
                TokenType.STRING,
                startLine,
                startColumn
        );
    }

    // Lê e valida um literal de caractere.
    private void readCharacter(int startLine, int startColumn) {

        int start = currentPosition;

        advance(); // Apóstrofo inicial.

        if (isAtEnd() || peek() == '\n' || peek() == '\'') {
            throw new RuntimeException(
                    "Literal de caractere inválido na linha " + startLine
            );
        }

        if (peek() == '\\') {
            advance();

            if (isAtEnd()) {
                throw new RuntimeException(
                        "Escape incompleto na linha " + startLine
                );
            }

            advance();

        } else {
            advance();
        }

        if (isAtEnd() || peek() != '\'') {
            throw new RuntimeException(
                    "Literal de caractere inválido na linha " + startLine
            );
        }

        advance(); // Apóstrofo final.

        String value = input.substring(start, currentPosition);

        addLiteralToken(
                value,
                TokenType.CARACTERE,
                startLine,
                startColumn
        );
    }

    // Lê um comentário de linha iniciado por //.
    private void readLineComment(int startLine, int startColumn) {

        int start = currentPosition;

        advance();
        advance();

        while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
            advance();
        }

        String value = input.substring(start, currentPosition);

        addToken(
                TokenType.COMENTARIO,
                value,
                startLine,
                startColumn,
                -1
        );
    }

    // Lê um comentário de bloco até encontrar */.
    private void readBlockComment(int startLine, int startColumn) {

        int start = currentPosition;

        advance();
        advance();

        while (!isAtEnd()) {

            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();

                String value = input.substring(start, currentPosition);

                addToken(
                        TokenType.COMENTARIO,
                        value,
                        startLine,
                        startColumn,
                        -1
                );

                return;
            }

            advance();
        }

        throw new RuntimeException(
                "Comentário de bloco não terminado na linha " + startLine
        );
    }

    // Reconhece operadores e delimitadores na posição atual.
    private boolean readOperatorOrDelimiter(
            int startLine,
            int startColumn
    ) {

        for (String operator : OPERATORS) {

            if (input.startsWith(operator, currentPosition)) {

                for (int i = 0; i < operator.length(); i++) {
                    advance();
                }

                addToken(
                        TokenType.OPERADOR,
                        operator,
                        startLine,
                        startColumn,
                        -1
                );

                return true;
            }
        }

        char current = peek();

        if ("(){}[];,.".indexOf(current) >= 0) {

            advance();

            addToken(
                    TokenType.DELIMITADOR,
                    String.valueOf(current),
                    startLine,
                    startColumn,
                    -1
            );

            return true;
        }

        return false;
    }

    // Registra o literal na tabela e cria um token que referencia seu índice.
    private void addLiteralToken(
            String value,
            TokenType type,
            int startLine,
            int startColumn
    ) {

        int index = symbolTable.addLiteral(value, type);

        addToken(
                type,
                "#" + index,
                startLine,
                startColumn,
                index
        );
    }

    // Centraliza a criação e a inclusão de tokens na lista.
    private void addToken(
            TokenType type,
            String value,
            int startLine,
            int startColumn,
            int tableIndex
    ) {

        tokens.add(
                new Token(
                        type,
                        value,
                        startLine,
                        startColumn,
                        tableIndex
                )
        );
    }

    // Consulta o caractere atual sem avançar a leitura.
    private char peek() {
        return input.charAt(currentPosition);
    }

    // Consulta o próximo caractere sem alterar a posição.
    private char peekNext() {

        if (currentPosition + 1 >= input.length()) {
            return '\0';
        }

        return input.charAt(currentPosition + 1);
    }

    // Avança um caractere e atualiza linha e coluna.
    private void advance() {

        char current = input.charAt(currentPosition++);

        if (current == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    // Verifica se todos os caracteres do código já foram lidos.
    private boolean isAtEnd() {
        return currentPosition >= input.length();
    }

    // Disponibiliza a tabela de símbolos e literais para consulta.
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
}
