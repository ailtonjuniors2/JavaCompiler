import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Parser {

    // Lista de tokens que será conferida (já sem os comentários).
    private final List<Token> tokens = new ArrayList<>();
    // Posição do token que estamos olhando agora (como um dedo sobre a lista).
    private int pos = 0;

    // Tipos básicos do Java; "void" entra porque pode ser tipo de retorno.
    private static final Set<String> TIPOS_PRIMITIVOS = Set.of(
            "int", "double", "float", "long", "short",
            "byte", "char", "boolean", "void");

    // Palavras que podem vir antes de classes, métodos e variáveis.
    private static final Set<String> MODIFICADORES = Set.of(
            "public", "private", "protected", "static", "final",
            "abstract", "synchronized", "native", "transient",
            "volatile", "strictfp");

    // Operadores que guardam um valor numa variável.
    private static final Set<String> ATRIBUICOES = Set.of(
            "=", "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=", "<<=", ">>=", ">>>=");

    // Operadores binários, do que "gruda menos" ao que "gruda mais" (precedência).
    // Ex.: em 1 + 2 * 3, o * está num nível mais baixo da lista, então é resolvido antes.
    private static final String[][] NIVEIS = {
            {"||"}, {"&&"}, {"|"}, {"^"}, {"&"},
            {"==", "!="},
            {"<", ">", "<=", ">="},
            {"<<", ">>", ">>>"},
            {"+", "-"},
            {"*", "/", "%"}
    };

    // Recebe os tokens do Lexer e descarta os comentários (não importam à sintaxe).
    public Parser(List<Token> todos) {
        for (Token t : todos) {
            if (t.getType() != TokenType.COMENTARIO) {
                tokens.add(t);
            }
        }
    }

    // ===== PONTO DE ENTRADA =====

    // Regra: programa = [package] {import} {classe}
    public void parse() {
        if (consumirSe("package")) {        // pacote é opcional
            nomeQualificado();              // ex.: meu.pacote
            esperar(";");
        }
        while (ehSimbolo("import")) {       // zero ou mais imports
            importacao();
        }
        while (!fim()) {                    // até acabar os tokens...
            declaracaoClasse();             // ...só aceitamos classes
        }
    }

    // Regra: import [static] nome[.*] ;
    private void importacao() {
        esperar("import");
        consumirSe("static");
        nomeQualificado();
        if (ehSimbolo(".") && ehSimboloEm(pos + 1, "*")) { // import java.util.*;
            avancar();
            avancar();
        }
        esperar(";");
    }

    // ===== CLASSES E MEMBROS =====

    // Regra: {modificador} class Nome [extends X] [implements Y, Z] { {membro} }
    private void declaracaoClasse() {
        modificadores();
        esperar("class");
        esperarIdentificador();
        if (consumirSe("extends")) {
            nomeQualificado();
        }
        if (consumirSe("implements")) {
            do {
                nomeQualificado();
            } while (consumirSe(","));      // lista separada por vírgulas
        }
        esperar("{");
        while (!ehSimbolo("}") && !fim()) { // lê membros até fechar a chave
            membro();
        }
        esperar("}");
    }

    // Membro = construtor, método, variável ou bloco inicializador.
    private void membro() {
        modificadores();

        if (ehSimbolo("{")) {               // bloco solto dentro da classe
            bloco();
            return;
        }

        // Construtor: nome da classe seguido de "(" (não tem tipo de retorno).
        if (atual().getType() == TokenType.IDENTIFICADOR && ehSimboloEm(pos + 1, "(")) {
            avancar();
            parametros();
            bloco();
            return;
        }

        tipo();                             // tipo da variável ou retorno do método
        esperarIdentificador();             // nome

        if (ehSimbolo("(")) {               // tem parênteses: é um método
            parametros();
            if (consumirSe("throws")) {
                do {
                    nomeQualificado();
                } while (consumirSe(","));
            }
            if (!consumirSe(";")) {         // método abstrato termina em ";"
                bloco();                    // senão tem corpo { ... }
            }
        } else {                            // sem parênteses: é uma variável
            restoDaDeclaracao();
            esperar(";");
        }
    }

    // Regra: ( [tipo nome {, tipo nome}] )
    private void parametros() {
        esperar("(");
        if (!ehSimbolo(")")) {
            do {
                modificadores();
                tipo();
                esperarIdentificador();
            } while (consumirSe(","));
        }
        esperar(")");
    }

    // Depois de "tipo nome": [= valor] {, outroNome [= valor]}
    private void restoDaDeclaracao() {
        inicializador();
        while (consumirSe(",")) {
            esperarIdentificador();
            inicializador();
        }
    }

    // Valor inicial opcional: = expressão
    private void inicializador() {
        if (consumirSe("=")) {
            expressao();
        }
    }

    // ===== TIPOS =====

    // Regra: tipoBase {[]}   (ex.: int, String, int[][])
    private void tipo() {
        tipoBase();
        while (ehSimbolo("[") && ehSimboloEm(pos + 1, "]")) {
            avancar();
            avancar();
        }
    }

    // Tipo básico (int...) ou nome de classe (String, java.util.List...).
    private void tipoBase() {
        if (ehPrimitivoEm(pos)) {
            avancar();
        } else if (atual().getType() == TokenType.IDENTIFICADOR) {
            nomeQualificado();
        } else {
            erro("um tipo");
        }
    }

    // Regra: Nome {. Nome}   (ex.: System.out)
    private void nomeQualificado() {
        esperarIdentificador();
        while (ehSimbolo(".") && tokenEm(pos + 1).getType() == TokenType.IDENTIFICADOR) {
            avancar();                      // ponto
            avancar();                      // nome seguinte
        }
    }

    // Pula modificadores como public, private, static...
    private void modificadores() {
        while (atual().getType() == TokenType.PALAVRA_CHAVE
                && MODIFICADORES.contains(atual().getValue())) {
            avancar();
        }
    }

    // ===== COMANDOS (instruções dentro dos métodos) =====

    // Regra: { {comando} }
    private void bloco() {
        esperar("{");
        while (!ehSimbolo("}") && !fim()) {
            comando();
        }
        esperar("}");
    }

    // Decide qual tipo de comando vem a seguir olhando a primeira palavra.
    private void comando() {
        if (ehSimbolo("{")) {
            bloco();

        } else if (consumirSe("if")) {      // if (cond) comando [else comando]
            esperar("(");
            expressao();
            esperar(")");
            comando();
            if (consumirSe("else")) {
                comando();
            }

        } else if (consumirSe("while")) {   // while (cond) comando
            esperar("(");
            expressao();
            esperar(")");
            comando();

        } else if (consumirSe("do")) {      // do comando while (cond);
            comando();
            esperar("while");
            esperar("(");
            expressao();
            esperar(")");
            esperar(";");

        } else if (consumirSe("for")) {     // for (inicio; condição; passo) comando
            esperar("(");
            if (!ehSimbolo(";")) {          // início pode ser vazio
                if (ehDeclaracaoLocal()) {
                    declaracaoLocal();      // ex.: int i = 0
                } else {
                    expressao();            // ex.: i = 0
                }
            }
            esperar(";");
            if (!ehSimbolo(";")) {
                expressao();                // condição
            }
            esperar(";");
            if (!ehSimbolo(")")) {
                expressao();                // passo (ex.: i++)
            }
            esperar(")");
            comando();

        } else if (consumirSe("return")) {  // return [valor];
            if (!ehSimbolo(";")) {
                expressao();
            }
            esperar(";");

        } else if (consumirSe("break") || consumirSe("continue")) {
            esperar(";");

        } else if (ehDeclaracaoLocal()) {   // int x = 5;
            declaracaoLocal();
            esperar(";");

        } else {                            // qualquer outra coisa: expressão + ";"
            expressao();
            esperar(";");
        }
    }

    // Regra: [final] tipo nome [= valor] {, nome [= valor]}
    private void declaracaoLocal() {
        modificadores();
        tipo();
        esperarIdentificador();
        restoDaDeclaracao();
    }

    // Espia à frente (sem avançar) para saber se é "Tipo nome" ou uma expressão.
    private boolean ehDeclaracaoLocal() {
        Token t = atual();

        if (t.getType() == TokenType.PALAVRA_CHAVE) {
            String v = t.getValue();
            return (TIPOS_PRIMITIVOS.contains(v) && !v.equals("void"))
                    || MODIFICADORES.contains(v);
        }
        if (t.getType() != TokenType.IDENTIFICADOR) {
            return false;
        }

        int i = pos + 1;                    // olha adiante com um "dedo" temporário
        while (ehSimboloEm(i, ".") && tokenEm(i + 1).getType() == TokenType.IDENTIFICADOR) {
            i += 2;                         // pula ".Nome"
        }
        while (ehSimboloEm(i, "[") && ehSimboloEm(i + 1, "]")) {
            i += 2;                         // pula "[]"
        }
        // Se depois do tipo vem um nome, é declaração: "String nome".
        return tokenEm(i).getType() == TokenType.IDENTIFICADOR;
    }

    // ===== EXPRESSÕES (contas, chamadas, atribuições) =====

    private void expressao() {
        atribuicao();
    }

    // Regra: ternario [operadorDeAtribuição atribuicao]   (ex.: x = y = 3)
    private void atribuicao() {
        ternario();
        Token t = atual();
        if (t.getType() == TokenType.OPERADOR && ATRIBUICOES.contains(t.getValue())) {
            avancar();
            atribuicao();
        }
    }

    // Regra: binario [? expressao : ternario]   (ex.: a > b ? a : b)
    private void ternario() {
        binario(0);
        if (consumirSe("?")) {
            expressao();
            esperar(":");
            ternario();
        }
    }

    // Trata operadores de dois lados, nível por nível, respeitando a precedência.
    private void binario(int nivel) {
        if (nivel == NIVEIS.length) {       // passou do último nível: vai ao mais forte
            unario();
            return;
        }
        binario(nivel + 1);                 // lado esquerdo (nível mais forte)
        while (operadorDoNivel(nivel)) {    // enquanto houver operador deste nível
            avancar();                      // consome o operador
            binario(nivel + 1);             // lado direito
        }
    }

    // Verifica se o token atual é um operador do nível informado.
    private boolean operadorDoNivel(int nivel) {
        for (String op : NIVEIS[nivel]) {
            if (ehSimbolo(op)) {
                return true;
            }
        }
        return false;
    }

    // Regra: (+ | - | ! | ~ | ++ | --) unario | posfixo
    private void unario() {
        if (ehSimbolo("+") || ehSimbolo("-") || ehSimbolo("!")
                || ehSimbolo("~") || ehSimbolo("++") || ehSimbolo("--")) {
            avancar();
            unario();
        } else {
            posfixo();
        }
    }

    // Regra: primario { .nome | (args) | [indice] | ++ | -- }
    private void posfixo() {
        primario();
        while (true) {
            if (consumirSe(".")) {
                esperarIdentificador();     // acesso: objeto.campo
            } else if (ehSimbolo("(")) {
                argumentos();               // chamada: metodo(...)
            } else if (consumirSe("[")) {
                expressao();                // acesso a vetor: v[i]
                esperar("]");
            } else if (consumirSe("++") || consumirSe("--")) {
                // incremento depois do valor: i++
            } else {
                break;                      // nada mais a encadear
            }
        }
    }

    // As "peças básicas" de uma expressão.
    private void primario() {
        TokenType tipo = atual().getType();

        // Números, textos, caracteres, true/false, null e nomes de variáveis.
        if (tipo == TokenType.NUMERAL || tipo == TokenType.STRING
                || tipo == TokenType.CARACTERE || tipo == TokenType.BOOLEANO
                || tipo == TokenType.NULO || tipo == TokenType.IDENTIFICADOR) {
            avancar();
            return;
        }
        if (consumirSe("this") || consumirSe("super")) {
            return;
        }
        if (consumirSe("new")) {            // new Pessoa(...) ou new int[5]
            tipoBase();
            if (ehSimbolo("(")) {
                argumentos();
            } else if (ehSimbolo("[")) {
                while (consumirSe("[")) {
                    if (!ehSimbolo("]")) {
                        expressao();
                    }
                    esperar("]");
                }
            } else {
                erro("'(' ou '['");
            }
            return;
        }
        if (ehSimbolo("(")) {
            if (ehPrimitivoEm(pos + 1)) {   // conversão: (int) valor
                avancar();
                tipo();
                esperar(")");
                unario();
            } else {                        // parênteses normais: (a + b)
                avancar();
                expressao();
                esperar(")");
            }
            return;
        }
        erro("uma expressão");
    }

    // Regra: ( [expressao {, expressao}] )
    private void argumentos() {
        esperar("(");
        if (!ehSimbolo(")")) {
            do {
                expressao();
            } while (consumirSe(","));
        }
        esperar(")");
    }

    // ===== FERRAMENTAS AUXILIARES =====

    // Retorna o token na posição i; se passou do fim, devolve um token "fim do arquivo".
    private Token tokenEm(int i) {
        if (i < tokens.size()) {
            return tokens.get(i);
        }
        int linha = 1, coluna = 1;
        if (!tokens.isEmpty()) {
            Token ultimo = tokens.get(tokens.size() - 1);
            linha = ultimo.getLine();
            coluna = ultimo.getColumn();
        }
        return new Token(TokenType.DESCONHECIDO, "fim do arquivo", linha, coluna, -1);
    }

    // Token atual.
    private Token atual() {
        return tokenEm(pos);
    }

    // Anda um token para a frente (se ainda houver).
    private void avancar() {
        if (!fim()) {
            pos++;
        }
    }

    // Já leu todos os tokens?
    private boolean fim() {
        return pos >= tokens.size();
    }

    // O token na posição i é palavra-chave, operador ou delimitador com este texto?
    // (Verificar o tipo evita confundir uma string "(" com o símbolo "(".)
    private boolean ehSimboloEm(int i, String valor) {
        Token t = tokenEm(i);
        TokenType tp = t.getType();
        return (tp == TokenType.PALAVRA_CHAVE
                || tp == TokenType.OPERADOR
                || tp == TokenType.DELIMITADOR)
                && t.getValue().equals(valor);
    }

    // Igual ao anterior, mas para o token atual.
    private boolean ehSimbolo(String valor) {
        return ehSimboloEm(pos, valor);
    }

    // O token na posição i é um tipo primitivo (int, double...)?
    private boolean ehPrimitivoEm(int i) {
        Token t = tokenEm(i);
        return t.getType() == TokenType.PALAVRA_CHAVE
                && TIPOS_PRIMITIVOS.contains(t.getValue());
    }

    // Se o token atual for o esperado, consome e devolve true; senão, devolve false.
    private boolean consumirSe(String valor) {
        if (ehSimbolo(valor)) {
            avancar();
            return true;
        }
        return false;
    }

    // Exige o símbolo; se não estiver lá, é erro sintático.
    private void esperar(String valor) {
        if (!ehSimbolo(valor)) {
            erro("'" + valor + "'");
        }
        avancar();
    }

    // Exige um identificador (nome de variável, método, classe).
    private void esperarIdentificador() {
        if (atual().getType() != TokenType.IDENTIFICADOR) {
            erro("um identificador");
        }
        avancar();
    }

    // Interrompe a análise com uma mensagem indicando onde está o problema.
    private void erro(String esperado) {
        Token t = atual();
        throw new RuntimeException(
                "Erro sintático na linha " + t.getLine()
                        + ", coluna " + t.getColumn()
                        + ": esperado " + esperado
                        + ", mas encontrei '" + t.getValue() + "'");
    }
}
