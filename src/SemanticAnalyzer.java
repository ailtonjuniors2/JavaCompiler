
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SemanticAnalyzer {

    private final List<Token> tokens;
    private final Map<String, String> variaveis = new HashMap<>();
    private final List<String> erros = new ArrayList<>();

    private static final Set<String> TIPOS = Set.of(
            "int", "double", "float", "long",
            "short", "byte", "char", "boolean", "String"
    );

    public SemanticAnalyzer(List<Token> tokens) {
        this.tokens = new ArrayList<>();

        for (Token token : tokens) {
            if (token.getType() != TokenType.COMENTARIO) {
                this.tokens.add(token);
            }
        }
    }

private void adicionarErro(String erro) {
    if (!erros.contains(erro)) {
        erros.add(erro);
    }
}

private void verificarIncompatibilidade(String tipoEsperado, String tipoRecebido, int linha) {
    if (!tiposCompativeis(tipoEsperado, tipoRecebido)) {
        String erro = "[ERRO SEMÂNTICO] Incompatibilidade de tipos na linha "
                + linha + ": Não é possível atribuir "
                + tipoRecebido + " a um tipo " + tipoEsperado + ".";

        adicionarErro(erro);
    }
}

public List<String> analisar() {

    variaveis.clear();
    erros.clear();

    for (int i = 0; i < tokens.size(); i++) {

        Token atual = tokens.get(i);

        // Verifica se o token representa uma declaração.
        if (ehDeclaracao(i)) {

            String tipo = atual.getValue();
            Token nome = tokens.get(i + 1);

            // Registra a variável antes de verificar sua inicialização.
            variaveis.put(nome.getValue(), tipo);

            // Verifica a compatibilidade do valor inicial.
            if (i + 2 < tokens.size()
                    && tokens.get(i + 2).getValue().equals("=")) {

                verificarAtribuicao(
                        nome.getValue(),
                        i + 3,
                        nome.getLine()
                );
            }

            // Pula o identificador que acabou de ser declarado.
            i++;
            continue;
        }

        // Verifica atribuições de variáveis já declaradas.
        if (atual.getType() == TokenType.IDENTIFICADOR
                && i + 1 < tokens.size()
                && tokens.get(i + 1).getValue().equals("=")) {

            String nome = atual.getValue();

            if (!variaveis.containsKey(nome)) {

                adicionarErro(
                        "[ERRO SEMÂNTICO] Variável '" + nome
                        + "' utilizada na linha " + atual.getLine()
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


    // Identifica declarações simples de variáveis.
    private boolean ehDeclaracao(int i) {

        if (i + 2 >= tokens.size()) {
            return false;
        }

        Token tipo = tokens.get(i);
        Token nome = tokens.get(i + 1);
        Token seguinte = tokens.get(i + 2);

        if (!TIPOS.contains(tipo.getValue())) {
            return false;
        }

        if (nome.getType() != TokenType.IDENTIFICADOR) {
            return false;
        }

        // Evita confundir métodos com declarações de variáveis.
        return seguinte.getValue().equals("=")
                || seguinte.getValue().equals(";")
                || seguinte.getValue().equals(",");
    }

    // Verifica se o tipo do valor é compatível com a variável.
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

    String tipoRecebido = identificarTipo(valor);

    // Se for uma variável, consulta seu tipo na tabela semântica.
    if (valor.getType() == TokenType.IDENTIFICADOR) {

        String tipoVariavel = variaveis.get(valor.getValue());

        if (tipoVariavel == null) {

            adicionarErro(
                "[ERRO SEMÂNTICO] Variável '"
                + valor.getValue()
                + "' utilizada na linha " + valor.getLine()
                + " não foi declarada previamente."
            );

            return;
        }

        tipoRecebido = tipoVariavel;
    }

    if (tipoRecebido == null) {
        return;
    }

    // Verifica a incompatibilidade de tipos.
    verificarIncompatibilidade(tipoEsperado, tipoRecebido, linha);
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

                if (numero.contains(".")
                        || numero.contains("e")
                        || numero.endsWith("f")
                        || numero.endsWith("d")) {
                    return numero.endsWith("f") ? "float" : "double";
                }

                if (numero.endsWith("l")) {
                    return "long";
                }

                return "int";

            default:
                return null;
        }
    }

    // Define quais conversões de tipos são permitidas.
    private boolean tiposCompativeis(
            String esperado,
            String recebido
    ) {

        if (esperado.equals(recebido)) {
            return true;
        }

        if (esperado.equals("double")) {
            return Set.of("byte", "short", "char", "int", "long", "float")
                    .contains(recebido);
        }

        if (esperado.equals("float")) {
            return Set.of("byte", "short", "char", "int", "long")
                    .contains(recebido);
        }

        if (esperado.equals("long")) {
            return Set.of("byte", "short", "char", "int")
                    .contains(recebido);
        }

        if (esperado.equals("int")) {
            return Set.of("byte", "short", "char")
                    .contains(recebido);
        }

        return false;
    }

    // Exibe o resultado da análise semântica.
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
