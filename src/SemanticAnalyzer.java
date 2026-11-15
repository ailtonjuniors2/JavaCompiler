
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SemanticAnalyzer {

    private final List<Token> tokens;
    private final Map<String, String> variaveis = new HashMap<>();
    private final List<String> erros = new ArrayList<>();

    private static final Set<String> TIPOS_PRIMITIVOS = Set.of(
            "int", "double", "float", "long",
            "short", "byte", "char", "boolean", "String"
    );

    private static final Set<String> PALAVRAS_RESERVADAS = Set.of(
            "return", "throw", "new", "this", "super",
            "if", "else", "while", "for", "do",
            "switch", "case", "default", "try",
            "catch", "finally", "break", "continue",
            "class", "interface", "enum", "import",
            "package", "extends", "implements", "throws",
            "void", "public", "private", "protected",
            "static", "final", "abstract", "synchronized",
            "instanceof", "assert"
    );

    public SemanticAnalyzer(List<Token> tokens) {

        this.tokens = new ArrayList<>();

        for (Token token : tokens) {
            if (token.getType() != TokenType.COMENTARIO) {
                this.tokens.add(token);
            }
        }
    }

    // Evita erros duplicados.
    private void adicionarErro(String erro) {

        if (!erros.contains(erro)) {
            erros.add(erro);
        }
    }

    // Procura declarações de variáveis, incluindo tipos genéricos e arrays.
    private int encontrarNomeDeclaracao(int inicio) {

        if (inicio >= tokens.size()) {
            return -1;
        }

        Token tipo = tokens.get(inicio);
        String valorTipo = tipo.getValue();

        if (PALAVRAS_RESERVADAS.contains(valorTipo)) {
            return -1;
        }

        if (tipo.getType() != TokenType.IDENTIFICADOR
                && !TIPOS_PRIMITIVOS.contains(valorTipo)) {
            return -1;
        }

        int posicao = inicio + 1;

        // Reconhece tipos genéricos: List<String>, Map<String, Integer>.
        if (posicao < tokens.size()
                && tokens.get(posicao).getValue().equals("<")) {

            int profundidade = 0;

            while (posicao < tokens.size()) {

                String atual = tokens.get(posicao).getValue();

                if (atual.equals("<")) {
                    profundidade++;
                }

                if (atual.equals(">")) {
                    profundidade--;

                    if (profundidade == 0) {
                        posicao++;
                        break;
                    }
                }

                posicao++;
            }

            if (profundidade != 0) {
                return -1;
            }
        }

        // Reconhece arrays: String[] args.
        while (posicao + 1 < tokens.size()
                && tokens.get(posicao).getValue().equals("[")
                && tokens.get(posicao + 1).getValue().equals("]")) {

            posicao += 2;
        }

        if (posicao >= tokens.size()) {
            return -1;
        }

        Token nome = tokens.get(posicao);

        if (nome.getType() != TokenType.IDENTIFICADOR) {
            return -1;
        }

        // Não confunde declarações com chamadas de métodos.
        if (posicao + 1 >= tokens.size()) {
            return -1;
        }

        String seguinte = tokens.get(posicao + 1).getValue();

        if (seguinte.equals("=")
                || seguinte.equals(";")
                || seguinte.equals(",")
                || seguinte.equals(")")
                || seguinte.equals(":")) {

            return posicao;
        }

        return -1;
    }

    // Monta o nome completo do tipo declarado.
    private String obterTipoDeclaracao(int inicio, int indiceNome) {

        StringBuilder tipo = new StringBuilder();

        for (int i = inicio; i < indiceNome; i++) {
            tipo.append(tokens.get(i).getValue());
        }

        return tipo.toString();
    }

    // Executa a análise semântica.
    public List<String> analisar() {

        variaveis.clear();
        erros.clear();

        for (int i = 0; i < tokens.size(); i++) {

            Token atual = tokens.get(i);

            int indiceNome = encontrarNomeDeclaracao(i);

            // Identifica uma declaração.
            if (indiceNome != -1) {

                String tipo = obterTipoDeclaracao(i, indiceNome);

                Token nome = tokens.get(indiceNome);

                variaveis.put(nome.getValue(), tipo);

                // Verifica a inicialização da variável.
                if (indiceNome + 1 < tokens.size()
                        && tokens.get(indiceNome + 1).getValue().equals("=")) {

                    verificarAtribuicao(
                            nome.getValue(),
                            indiceNome + 2,
                            nome.getLine()
                    );
                }

                i = indiceNome;
                continue;
            }

            // Verifica atribuições de variáveis.
            if (atual.getType() == TokenType.IDENTIFICADOR
                    && i + 1 < tokens.size()
                    && tokens.get(i + 1).getValue().equals("=")) {

                String nome = atual.getValue();

                if (!variaveis.containsKey(nome)) {

                    adicionarErro(
                            "[ERRO SEMÂNTICO] Variável '"
                            + nome
                            + "' utilizada na linha "
                            + atual.getLine()
                            + " não foi declarada previamente."
                    );

                } else {

                    verificarAtribuicao(
                            nome,
                            i + 2,
                            atual.getLine()
                    );
                }
            }
        }

        return new ArrayList<>(erros);
    }

    // Verifica a compatibilidade do valor atribuído.
   



private void verificarAtribuicao(
        String nome,
        int posicaoValor,
        int linha
) {
    if (posicaoValor >= tokens.size()) {
        return;
    }

    String tipoEsperado = variaveis.get(nome);
    Token valor = tokens.get(posicaoValor);

    // Verifica se a expressão começa com uma variável.
    if (valor.getType() == TokenType.IDENTIFICADOR
            && !valor.getValue().equals("new")) {

        boolean chamadaDireta = posicaoValor + 1 < tokens.size()
                && tokens.get(posicaoValor + 1).getValue().equals("(");

        boolean acessoOuChamada = posicaoValor + 1 < tokens.size()
                && tokens.get(posicaoValor + 1).getValue().equals(".");

        if (!chamadaDireta && !variaveis.containsKey(valor.getValue())) {
            adicionarErro(
                    "[ERRO SEMÂNTICO] Variável '"
                    + valor.getValue()
                    + "' utilizada na linha "
                    + valor.getLine()
                    + " não foi declarada previamente."
            );
            return;
        }
    }

    String tipoRecebido = inferirTipoExpressao(posicaoValor);

    if (tipoRecebido != null) {
        verificarIncompatibilidade(
                tipoEsperado,
                tipoRecebido,
                linha
        );
    }
}

// Identifica o tipo resultante de uma expressão.
private String inferirTipoExpressao(int inicio) {

    if (inicio >= tokens.size()) {
        return null;
    }

    Token primeiro = tokens.get(inicio);

    // Trata instanciação de objetos: new Main().
    if (primeiro.getValue().equals("new")
            && inicio + 1 < tokens.size()) {
        return tokens.get(inicio + 1).getValue();
    }

    String ultimoMetodo = null;

    // Procura chamadas de métodos até o final da expressão.
    for (int i = inicio; i < tokens.size(); i++) {

        String atual = tokens.get(i).getValue();

        if (atual.equals(";")) {
            break;
        }

        if (tokens.get(i).getType() == TokenType.IDENTIFICADOR
                && i + 1 < tokens.size()
                && tokens.get(i + 1).getValue().equals("(")) {

            ultimoMetodo = atual;
        }
    }

    // Métodos conhecidos da API Java.
    if (ultimoMetodo != null) {

        if (ultimoMetodo.equals("count")) {
            return "long";
        }

        // Procura o tipo de retorno de métodos declarados no código.
        String tipoRetorno = encontrarTipoRetornoMetodo(ultimoMetodo);

        if (tipoRetorno != null) {
            return tipoRetorno;
        }

        return null;
    }

    // Se for uma variável, consulta seu tipo declarado.
    if (primeiro.getType() == TokenType.IDENTIFICADOR) {
        return variaveis.get(primeiro.getValue());
    }

    // Se for um literal, identifica seu tipo.
    return identificarTipo(primeiro);
}

// Procura a declaração de um método e obtém seu tipo de retorno.
private String encontrarTipoRetornoMetodo(String nomeMetodo) {

    for (int i = 1; i < tokens.size() - 1; i++) {

        if (!tokens.get(i).getValue().equals(nomeMetodo)) {
            continue;
        }

        // Uma declaração de método tem o tipo antes do nome
        // e parênteses logo depois dele.
        if (!tokens.get(i + 1).getValue().equals("(")) {
            continue;
        }

        String possivelTipo = tokens.get(i - 1).getValue();

        // Evita confundir uma chamada com uma declaração.
        if (possivelTipo.equals(".")
                || PALAVRAS_RESERVADAS.contains(possivelTipo)
                || possivelTipo.equals("new")) {
            continue;
        }

        if (TIPOS_PRIMITIVOS.contains(possivelTipo)
                || tokens.get(i - 1).getType() == TokenType.IDENTIFICADOR) {
            return possivelTipo;
        }
    }

    return null;
}



    // Verifica se os tipos são compatíveis.
    private void verificarIncompatibilidade(
            String tipoEsperado,
            String tipoRecebido,
            int linha
    ) {

        if (!tiposCompativeis(tipoEsperado, tipoRecebido)) {

            adicionarErro(
                    "[ERRO SEMÂNTICO] Incompatibilidade de tipos na linha "
                    + linha
                    + ": Não é possível atribuir "
                    + tipoRecebido
                    + " a um tipo "
                    + tipoEsperado
                    + "."
            );
        }
    }

    // Identifica o tipo de um literal.
    private String identificarTipo(Token token) {

        switch (token.getType()) {

            case STRING:
                return "String";

            case CARACTERE:
                return "char";

            case BOOLEANO:
                return "boolean";

            case NUMERAL:

                String numero = token.getValue().toLowerCase();

                if (numero.endsWith("f")) {
                    return "float";
                }

                if (numero.endsWith("d")
                        || numero.contains(".")
                        || numero.contains("e")) {
                    return "double";
                }

                if (numero.endsWith("l")) {
                    return "long";
                }

                return "int";

            default:
                return null;
        }
    }

    // Define as conversões numéricas permitidas.
   
private boolean tiposCompativeis(
        String esperado,
        String recebido
) {

    if (esperado.equals(recebido)) {
        return true;
    }

    // Remove os parâmetros genéricos para comparar os tipos-base.
    String tipoEsperadoBase = esperado.replaceAll("<.*>", "");
    String tipoRecebidoBase = recebido.replaceAll("<.*>", "");

    // Verifica implementações de interfaces e classes.
    if (tipoEsperadoBase.equals("List")
            && Set.of("ArrayList", "LinkedList", "Vector")
                    .contains(tipoRecebidoBase)) {
        return true;
    }

    if (tipoEsperadoBase.equals("Map")
            && Set.of("HashMap", "TreeMap", "LinkedHashMap")
                    .contains(tipoRecebidoBase)) {
        return true;
    }

    if (tipoEsperadoBase.equals("Set")
            && Set.of("HashSet", "TreeSet", "LinkedHashSet")
                    .contains(tipoRecebidoBase)) {
        return true;
    }

    // Conversões numéricas permitidas.
    Set<String> conversoes = new HashSet<>();

    switch (esperado) {

        case "double":
            conversoes.addAll(Set.of(
                    "byte", "short", "char",
                    "int", "long", "float"
            ));
            break;

        case "float":
            conversoes.addAll(Set.of(
                    "byte", "short", "char",
                    "int", "long"
            ));
            break;

        case "long":
            conversoes.addAll(Set.of(
                    "byte", "short", "char", "int"
            ));
            break;

        case "int":
            conversoes.addAll(Set.of(
                    "byte", "short", "char"
            ));
            break;

        default:
            return false;
    }

    return conversoes.contains(recebido);
}

    // Exibe o resultado da análise.
    public void exibirResultado() {

        List<String> resultados = analisar();

        System.out.println("\n===== ANÁLISE SEMÂNTICA =====");

        if (resultados.isEmpty()) {

            System.out.println(
                    "Análise semântica concluída sem erros."
            );

        } else {

            for (String erro : resultados) {
                System.out.println(erro);
            }

            System.out.println(
                    "Total de erros semânticos: " + resultados.size()
            );
        }
    }
}

