import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Parser {

    private final List<Token> tokens = new ArrayList<>();
    private final SymbolTable tabela;
    private int pos = 0;

    private static final Set<String> TIPOS_PRIMITIVOS = Set.of(
            "int", "double", "float", "long", "short",
            "byte", "char", "boolean", "void");

    private static final Set<String> MODIFICADORES = Set.of(
            "public", "private", "protected", "static", "final",
            "abstract", "synchronized", "native", "transient",
            "volatile", "strictfp", "default");

    private static final Set<String> ATRIBUICOES = Set.of(
            "=", "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=", "<<=", ">>=", ">>>=");

    private static final String[][] NIVEIS = {
            {"||"}, {"&&"}, {"|"}, {"^"}, {"&"},
            {"==", "!="},
            {"<", ">", "<=", ">="},
            {"<<", ">>", ">>>"},
            {"+", "-"},
            {"*", "/", "%"}
    };
    private static final int NIVEL_RELACIONAL = 6; // onde mora o instanceof

    public Parser(List<Token> todos, SymbolTable tabela) {
        this.tabela = tabela;
        for (Token t : todos) {
            if (t.getType() != TokenType.COMENTARIO) {
                tokens.add(t);
            }
        }
    }

    // ===================== PROGRAMA =====================

    public Node parse() {
        Node raiz = new Node("Programa");
        if (consumirSe("package")) {
            raiz.add(new Node("Package: " + nomeQualificadoTexto()));
            esperar(";");
        }
        while (ehSimbolo("import") || ehSimbolo(";")) {
            if (consumirSe(";")) {
                continue;
            }
            raiz.add(importacao());
        }
        while (!fim()) {
            if (consumirSe(";")) {
                continue;
            }
            Node mods = modificadores();
            raiz.add(declaracaoTipo(mods));
        }
        return raiz;
    }

    private Node importacao() {
        Node no = new Node("Import");
        esperar("import");
        if (consumirSe("static")) {
            no.add(new Node("static"));
        }
        String nome = nomeQualificadoTexto();
        if (ehSimbolo(".") && ehSimboloEm(pos + 1, "*")) {
            avancar();
            avancar();
            nome += ".*";
        }
        esperar(";");
        return no.add(new Node("Nome: " + nome));
    }

    // ===================== DECLARAÇÕES DE TIPO =====================

    private Node declaracaoTipo(Node mods) {
        if (consumirSe("class")) {
            return classe(mods);
        }
        if (consumirSe("interface")) {
            return interfaceDecl(mods);
        }
        if (consumirSe("enum")) {
            return enumeracao(mods);
        }
        throw erro("uma declaração de tipo");
    }

    private Node classe(Node mods) {
        Node no = new Node("Classe").add(mods);
        no.add(new Node("Nome: " + esperarIdentificador()));
        if (ehSimbolo("<")) {
            no.add(parametrosDeTipo());
        }
        clausula(no, "extends", "Extends");
        clausula(no, "implements", "Implements");
        corpoClasse(no);
        return no;
    }

    private Node interfaceDecl(Node mods) {
        Node no = new Node("Interface").add(mods);
        no.add(new Node("Nome: " + esperarIdentificador()));
        if (ehSimbolo("<")) {
            no.add(parametrosDeTipo());
        }
        clausula(no, "extends", "Extends");
        corpoClasse(no);
        return no;
    }

    private Node enumeracao(Node mods) {
        Node no = new Node("Enum").add(mods);
        no.add(new Node("Nome: " + esperarIdentificador()));
        clausula(no, "implements", "Implements");
        esperar("{");
        while (ehIdent(pos)) {
            Node c = new Node("Constante");
            c.add(new Node("Nome: " + esperarIdentificador()));
            if (ehSimbolo("(")) {
                c.add(argumentos());
            }
            if (ehSimbolo("{")) {
                Node corpo = new Node("CorpoConstante");
                corpoClasse(corpo);
                c.add(corpo);
            }
            no.add(c);
            if (!consumirSe(",")) {
                break;
            }
        }
        if (consumirSe(";")) {
            while (!ehSimbolo("}") && !fim()) {
                no.add(membro());
            }
        }
        esperar("}");
        return no;
    }

    // Lê "palavra Tipo {, Tipo}" (extends, implements) se existir.
    private void clausula(Node pai, String palavra, String rotulo) {
        if (!ehSimbolo(palavra)) {
            return;
        }
        avancar();
        Node n = new Node(rotulo);
        do {
            n.add(new Node("Tipo: " + tipoTexto()));
        } while (consumirSe(","));
        pai.add(n);
    }

    private void corpoClasse(Node no) {
        esperar("{");
        while (!ehSimbolo("}") && !fim()) {
            no.add(membro());
        }
        esperar("}");
    }

    private boolean ehTipoDeclarado() {
        return ehSimbolo("class") || ehSimbolo("interface") || ehSimbolo("enum");
    }

    // ===================== MEMBROS =====================

    private Node membro() {
        if (consumirSe(";")) {
            return null;
        }
        Node mods = modificadores();

        if (ehSimbolo("{")) {                              // bloco inicializador
            return new Node("Inicializador").add(mods).add(bloco());
        }
        if (ehTipoDeclarado()) {                           // tipo aninhado
            return declaracaoTipo(mods);
        }

        Node tp = ehSimbolo("<") ? parametrosDeTipo() : null;

        if (ehIdent(pos) && ehSimboloEm(pos + 1, "(")) {   // construtor
            Node no = new Node("Construtor").add(mods).add(tp);
            no.add(new Node("Nome: " + esperarIdentificador()));
            no.add(parametros());
            if (consumirSe("throws")) {
                no.add(listaThrows());
            }
            no.add(bloco());
            return no;
        }

        Node tipo = tipo();
        String nome = esperarIdentificador();

        if (ehSimbolo("(")) {                              // método
            Node no = new Node("Metodo").add(mods).add(tp).add(tipo);
            no.add(new Node("Nome: " + nome));
            no.add(parametros());
            while (ehSimbolo("[") && ehSimboloEm(pos + 1, "]")) {
                avancar();
                avancar();
            }
            if (consumirSe("throws")) {
                no.add(listaThrows());
            }
            if (!consumirSe(";")) {
                no.add(bloco());
            }
            return no;
        }

        Node no = new Node("DeclaracaoVariavel").add(mods).add(tipo);
        restoDaDeclaracao(no, nome);
        esperar(";");
        return no;
    }

    private Node listaThrows() {
        Node th = new Node("Throws");
        do {
            th.add(new Node("Tipo: " + tipoTexto()));
        } while (consumirSe(","));
        return th;
    }

    private Node parametros() {
        Node no = new Node("Parametros");
        esperar("(");
        if (!ehSimbolo(")")) {
            do {
                Node p = new Node("Parametro");
                p.add(modificadores());
                p.add(tipo());
                if (consumirSe("...")) {
                    p.add(new Node("Varargs"));
                }
                p.add(new Node("Nome: " + esperarIdentificador()));
                while (ehSimbolo("[") && ehSimboloEm(pos + 1, "]")) {
                    avancar();
                    avancar();
                }
                no.add(p);
            } while (consumirSe(","));
        }
        esperar(")");
        return no;
    }

    // Declara variáveis: nome [[]] [= valor] {, nome [= valor]}
    private void restoDaDeclaracao(Node pai, String primeiroNome) {
        String nome = primeiroNome;
        while (true) {
            Node v = new Node("Variavel: " + nome);
            while (ehSimbolo("[") && ehSimboloEm(pos + 1, "]")) {
                avancar();
                avancar();
                v.add(new Node("[]"));
            }
            if (consumirSe("=")) {
                v.add(inicializadorVar());
            }
            pai.add(v);
            if (!consumirSe(",")) {
                break;
            }
            nome = esperarIdentificador();
        }
    }

    private Node inicializadorVar() {
        return ehSimbolo("{") ? inicializadorArray() : expressao();
    }

    private Node inicializadorArray() {
        Node no = new Node("InicializadorArray");
        esperar("{");
        while (!ehSimbolo("}")) {
            no.add(inicializadorVar());
            if (!consumirSe(",")) {
                break;
            }
        }
        esperar("}");
        return no;
    }

    // ===================== TIPOS E GENERICS =====================

    private Node tipo() {
        return new Node("Tipo: " + tipoTexto());
    }

    private String tipoTexto() {
        String t = tipoBase();
        while (ehSimbolo("[") && ehSimboloEm(pos + 1, "]")) {
            avancar();
            avancar();
            t += "[]";
        }
        return t;
    }

    private String tipoBase() {
        if (ehPrimitivoEm(pos)) {
            String v = atual().getValue();
            avancar();
            return v;
        }
        if (ehIdent(pos)) {
            StringBuilder sb = new StringBuilder(esperarIdentificador());
            while (true) {
                if (ehSimbolo("<")) {
                    sb.append(argumentosDeTipo());
                }
                if (ehSimbolo(".") && ehIdent(pos + 1)) {
                    avancar();
                    sb.append('.').append(esperarIdentificador());
                } else {
                    break;
                }
            }
            return sb.toString();
        }
        throw erro("um tipo");
    }

    // <>, <String>, <String, List<Integer>>, <? extends X>
    private String argumentosDeTipo() {
        StringBuilder sb = new StringBuilder("<");
        esperar("<");
        if (!ehSimbolo(">") && !ehSimbolo(">>") && !ehSimbolo(">>>")) {
            boolean primeiro = true;
            do {
                if (!primeiro) {
                    sb.append(", ");
                }
                primeiro = false;
                if (consumirSe("?")) {
                    sb.append("?");
                    if (ehSimbolo("extends") || ehSimbolo("super")) {
                        sb.append(' ').append(atual().getValue()).append(' ');
                        avancar();
                        sb.append(tipoTexto());
                    }
                } else {
                    sb.append(tipoTexto());
                }
            } while (consumirSe(","));
        }
        fecharGenerics();
        return sb.append(">").toString();
    }

    // <T, U extends Comparable<U> & Serializable>
    private Node parametrosDeTipo() {
        List<String> itens = new ArrayList<>();
        esperar("<");
        do {
            StringBuilder sb = new StringBuilder(esperarIdentificador());
            if (consumirSe("extends")) {
                sb.append(" extends ").append(tipoTexto());
                while (consumirSe("&")) {
                    sb.append(" & ").append(tipoTexto());
                }
            }
            itens.add(sb.toString());
        } while (consumirSe(","));
        fecharGenerics();
        return new Node("ParametrosDeTipo: <" + String.join(", ", itens) + ">");
    }

    // Consome UM ">" mesmo que o Lexer o tenha juntado em ">>" ou ">>>".
    private void fecharGenerics() {
        Token t = atual();
        if (ehSimbolo(">")) {
            avancar();
        } else if (ehSimbolo(">>")) {
            tokens.set(pos, new Token(TokenType.OPERADOR, ">", t.getLine(), t.getColumn() + 1, -1));
        } else if (ehSimbolo(">>>")) {
            tokens.set(pos, new Token(TokenType.OPERADOR, ">>", t.getLine(), t.getColumn() + 1, -1));
        } else {
            throw erro("'>'");
        }
    }

    // Modificadores (public, static, final...). Devolve null se não houver nenhum.
    private Node modificadores() {
        List<String> nomes = new ArrayList<>();
        while (atual().getType() == TokenType.PALAVRA_CHAVE
                && MODIFICADORES.contains(atual().getValue())) {
            nomes.add(atual().getValue());
            avancar();
        }
        return nomes.isEmpty() ? null : new Node("Modificadores: " + String.join(" ", nomes));
    }

    private String nomeQualificadoTexto() {
        StringBuilder sb = new StringBuilder(esperarIdentificador());
        while (ehSimbolo(".") && ehIdent(pos + 1)) {
            avancar();
            sb.append('.').append(esperarIdentificador());
        }
        return sb.toString();
    }

    // ===================== COMANDOS =====================

    private Node bloco() {
        Node no = new Node("Bloco");
        esperar("{");
        while (!ehSimbolo("}") && !fim()) {
            no.add(comando());
        }
        esperar("}");
        return no;
    }

    private Node comando() {
        if (ehSimbolo("{")) {
            return bloco();
        }
        if (consumirSe(";")) {
            return new Node("Vazio");
        }

        if (consumirSe("if")) {
            Node no = new Node("If");
            esperar("(");
            no.add(new Node("Condicao").add(expressao()));
            esperar(")");
            no.add(new Node("Entao").add(comando()));
            if (consumirSe("else")) {
                no.add(new Node("Senao").add(comando()));
            }
            return no;
        }

        if (consumirSe("while")) {
            Node no = new Node("While");
            esperar("(");
            no.add(new Node("Condicao").add(expressao()));
            esperar(")");
            no.add(new Node("Corpo").add(comando()));
            return no;
        }

        if (consumirSe("do")) {
            Node no = new Node("DoWhile");
            no.add(new Node("Corpo").add(comando()));
            esperar("while");
            esperar("(");
            no.add(new Node("Condicao").add(expressao()));
            esperar(")");
            esperar(";");
            return no;
        }

        if (consumirSe("for")) {
            return comandoFor();
        }

        if (ehSimbolo("switch")) {
            return switchCorpo();
        }

        if (consumirSe("return")) {
            Node no = new Node("Return");
            if (!ehSimbolo(";")) {
                no.add(expressao());
            }
            esperar(";");
            return no;
        }

        if (consumirSe("break")) {
            Node no = new Node("Break");
            if (ehIdent(pos)) {
                no.add(new Node("Rotulo: " + esperarIdentificador()));
            }
            esperar(";");
            return no;
        }

        if (consumirSe("continue")) {
            Node no = new Node("Continue");
            if (ehIdent(pos)) {
                no.add(new Node("Rotulo: " + esperarIdentificador()));
            }
            esperar(";");
            return no;
        }

        if (consumirSe("throw")) {
            Node no = new Node("Throw").add(expressao());
            esperar(";");
            return no;
        }

        if (ehSimbolo("try")) {
            return comandoTry();
        }

        if (ehSimbolo("synchronized") && ehSimboloEm(pos + 1, "(")) {
            avancar();
            Node no = new Node("Synchronized");
            esperar("(");
            no.add(new Node("Monitor").add(expressao()));
            esperar(")");
            no.add(bloco());
            return no;
        }

        if (consumirSe("assert")) {
            Node no = new Node("Assert").add(expressao());
            if (consumirSe(":")) {
                no.add(expressao());
            }
            esperar(";");
            return no;
        }

        if (ehIdent(pos) && ehSimboloEm(pos + 1, ":")) {   // rótulo: nome: comando
            String nome = esperarIdentificador();
            avancar();
            return new Node("Rotulado: " + nome).add(comando());
        }

        if (ehTipoLocal()) {                               // classe/enum/interface local
            Node mods = modificadores();
            return declaracaoTipo(mods);
        }

        if (ehDeclaracaoLocal()) {
            Node no = declaracaoLocal();
            esperar(";");
            return no;
        }

        Node no = new Node("ComandoExpressao").add(expressao());
        esperar(";");
        return no;
    }

    private Node comandoFor() {
        esperar("(");
        if (ehForEach()) {
            Node no = new Node("ForEach");
            Node mods = modificadores();
            Node tp = tipo();
            String nome = esperarIdentificador();
            esperar(":");
            no.add(new Node("Variavel: " + nome).add(mods).add(tp));
            no.add(new Node("Iteravel").add(expressao()));
            esperar(")");
            no.add(new Node("Corpo").add(comando()));
            return no;
        }
        Node no = new Node("For");
        if (!ehSimbolo(";")) {
            Node ini = new Node("Inicio");
            if (ehDeclaracaoLocal()) {
                ini.add(declaracaoLocal());
            } else {
                listaExpressoes(ini);
            }
            no.add(ini);
        }
        esperar(";");
        if (!ehSimbolo(";")) {
            no.add(new Node("Condicao").add(expressao()));
        }
        esperar(";");
        if (!ehSimbolo(")")) {
            Node passo = new Node("Passo");
            listaExpressoes(passo);
            no.add(passo);
        }
        esperar(")");
        no.add(new Node("Corpo").add(comando()));
        return no;
    }

    private void listaExpressoes(Node pai) {
        do {
            pai.add(expressao());
        } while (consumirSe(","));
    }

    private Node comandoTry() {
        esperar("try");
        Node no = new Node("Try");
        boolean temRecursos = false;
        if (consumirSe("(")) {
            temRecursos = true;
            Node rec = new Node("Recursos");
            while (!ehSimbolo(")")) {
                if (ehDeclaracaoLocal()) {
                    rec.add(declaracaoLocal());
                } else {
                    rec.add(expressao());
                }
                if (!consumirSe(";")) {
                    break;
                }
            }
            esperar(")");
            no.add(rec);
        }
        no.add(bloco());
        boolean temFim = temRecursos;
        while (consumirSe("catch")) {
            Node c = new Node("Catch");
            esperar("(");
            c.add(modificadores());
            StringBuilder tipos = new StringBuilder(tipoTexto());
            while (consumirSe("|")) {
                tipos.append(" | ").append(tipoTexto());
            }
            c.add(new Node("Tipo: " + tipos));
            c.add(new Node("Nome: " + esperarIdentificador()));
            esperar(")");
            c.add(bloco());
            no.add(c);
            temFim = true;
        }
        if (consumirSe("finally")) {
            no.add(new Node("Finally").add(bloco()));
            temFim = true;
        }
        if (!temFim) {
            throw erro("'catch' ou 'finally'");
        }
        return no;
    }

    // ---------- switch (comando e expressão) ----------

    private Node switchCorpo() {
        esperar("switch");
        Node no = new Node("Switch");
        esperar("(");
        no.add(new Node("Seletor").add(expressao()));
        esperar(")");
        esperar("{");
        while (!ehSimbolo("}") && !fim()) {
            no.add(grupoSwitch());
        }
        esperar("}");
        return no;
    }

    private Node grupoSwitch() {
        Node grupo = new Node("Caso");
        Node rotulos = new Node("Rotulos");
        if (consumirSe("default")) {
            rotulos.add(new Node("default"));
        } else {
            esperar("case");
            do {
                rotulos.add(rotuloCase());
            } while (consumirSe(","));
        }
        grupo.add(rotulos);

        if (consumirSe("->")) {
            Node corpo = new Node("Seta");
            if (ehSimbolo("{")) {
                corpo.add(bloco());
            } else if (ehSimbolo("throw")) {
                corpo.add(comando());
            } else {
                corpo.add(expressao());
                esperar(";");
            }
            grupo.add(corpo);
        } else {
            esperar(":");
            Node corpo = new Node("Comandos");
            while (!ehSimbolo("case") && !ehSimbolo("default")
                    && !ehSimbolo("}") && !fim()) {
                corpo.add(comando());
            }
            grupo.add(corpo);
        }
        return grupo;
    }

    private Node rotuloCase() {
        if (consumirSe("default")) {
            return new Node("default");
        }
        return ehPadrao() ? padrao() : ternario();
    }

    private boolean ehPadrao() {
        int i = pularModificadoresEm(pos);
        int j = skipType(i);
        return j > 0 && ehIdent(j);
    }

    // Padrão de tipo: Tipo nome
    private Node padrao() {
        Node mods = modificadores();
        Node no = new Node("Padrao: " + tipoTexto()).add(mods);
        if (ehIdent(pos)) {
            no.add(new Node("Nome: " + esperarIdentificador()));
        }
        return no;
    }

    // ---------- declarações locais ----------

    private Node declaracaoLocal() {
        Node no = new Node("DeclaracaoLocal");
        no.add(modificadores());
        no.add(tipo());
        String nome = esperarIdentificador();
        restoDaDeclaracao(no, nome);
        return no;
    }

    private boolean ehDeclaracaoLocal() {
        int i = pularModificadoresEm(pos);
        int j = skipType(i);
        return j > 0 && ehIdent(j);
    }

    private boolean ehForEach() {
        int i = pularModificadoresEm(pos);
        int j = skipType(i);
        return j > 0 && ehIdent(j) && ehSimboloEm(j + 1, ":");
    }

    private boolean ehTipoLocal() {
        int i = pularModificadoresEm(pos);
        return ehSimboloEm(i, "class") || ehSimboloEm(i, "interface") || ehSimboloEm(i, "enum");
    }

    // ===================== EXPRESSÕES =====================

    private Node expressao() {
        if (ehLambda()) {
            return lambda();
        }
        return atribuicao();
    }

    private Node atribuicao() {
        Node esq = ternario();
        Token t = atual();
        if (t.getType() == TokenType.OPERADOR && ATRIBUICOES.contains(t.getValue())) {
            String op = t.getValue();
            avancar();
            Node dir = expressao();
            return new Node("Atribuicao (" + op + ")").add(esq).add(dir);
        }
        return esq;
    }

    private Node ternario() {
        Node cond = binario(0);
        if (consumirSe("?")) {
            Node no = new Node("Ternario").add(cond).add(expressao());
            esperar(":");
            no.add(ehLambda() ? lambda() : ternario());
            return no;
        }
        return cond;
    }

    private Node binario(int nivel) {
        if (nivel == NIVEIS.length) {
            return unario();
        }
        Node esq = binario(nivel + 1);
        while (true) {
            if (nivel == NIVEL_RELACIONAL && ehSimbolo("instanceof")) {
                avancar();
                esq = new Node("InstanceOf").add(esq).add(padrao());
            } else if (operadorDoNivel(nivel)) {
                String op = atual().getValue();
                avancar();
                Node dir = binario(nivel + 1);
                esq = new Node("Binario (" + op + ")").add(esq).add(dir);
            } else {
                break;
            }
        }
        return esq;
    }

    private boolean operadorDoNivel(int nivel) {
        for (String op : NIVEIS[nivel]) {
            if (ehSimbolo(op)) {
                return true;
            }
        }
        return false;
    }

    private Node unario() {
        if (ehSimbolo("+") || ehSimbolo("-") || ehSimbolo("!")
                || ehSimbolo("~") || ehSimbolo("++") || ehSimbolo("--")) {
            String op = atual().getValue();
            avancar();
            return new Node("Unario (" + op + ")").add(unario());
        }
        if (ehSimbolo("(") && ehCast()) {
            avancar();
            StringBuilder tp = new StringBuilder(tipoTexto());
            while (consumirSe("&")) {
                tp.append(" & ").append(tipoTexto());
            }
            esperar(")");
            Node operando = ehLambda() ? lambda() : unario();
            return new Node("Cast").add(new Node("Tipo: " + tp)).add(operando);
        }
        return posfixo();
    }

    private Node posfixo() {
        Node no = primario();
        while (true) {
            if (consumirSe(".")) {
                if (consumirSe("new")) {
                    no = new Node("NewInterno").add(no).add(criacao());
                } else if (ehSimbolo("<")) {
                    String ta = argumentosDeTipo();
                    no = new Node("Acesso (.)").add(no)
                            .add(new Node("TiposGenericos: " + ta))
                            .add(new Node("Nome: " + esperarIdentificador()));
                } else if (consumirSe("class")) {
                    no = new Node("ClassLiteral").add(no);
                } else if (consumirSe("this")) {
                    no = new Node("ThisQualificado").add(no);
                } else if (consumirSe("super")) {
                    no = new Node("SuperQualificado").add(no);
                } else {
                    no = new Node("Acesso (.)").add(no)
                            .add(new Node("Nome: " + esperarIdentificador()));
                }
            } else if (ehSimbolo("(")) {
                no = new Node("Chamada").add(no).add(argumentos());
            } else if (consumirSe("[")) {
                Node indice = expressao();
                esperar("]");
                no = new Node("Indice").add(no).add(indice);
            } else if (ehSimbolo("++") || ehSimbolo("--")) {
                String op = atual().getValue();
                avancar();
                no = new Node("PosFixo (" + op + ")").add(no);
            } else if (consumirSe("::")) {
                if (ehSimbolo("<")) {
                    argumentosDeTipo();
                }
                String nome = consumirSe("new") ? "new" : esperarIdentificador();
                no = new Node("ReferenciaMetodo (::)").add(no).add(new Node("Nome: " + nome));
            } else {
                break;
            }
        }
        return no;
    }

    private Node primario() {
        Token t = atual();
        TokenType tp = t.getType();

        if (tp == TokenType.IDENTIFICADOR) {
            // Tipo com [] ou <> usado em X[]::new, X[].class, List<String>::new
            int j = skipType(pos);
            if (j > 0 && (ehSimboloEm(j - 1, "]") || ehSimboloEm(j - 1, ">")
                    || ehSimboloEm(j - 1, ">>") || ehSimboloEm(j - 1, ">>>"))
                    && (ehSimboloEm(j, "::")
                        || (ehSimboloEm(j, ".") && ehSimboloEm(j + 1, "class")))) {
                return new Node("Tipo: " + tipoTexto());
            }
            avancar();
            return new Node("Identificador: " + t.getValue());
        }

        if (tp == TokenType.NUMERAL || tp == TokenType.STRING
                || tp == TokenType.CARACTERE || tp == TokenType.BOOLEANO
                || tp == TokenType.NULO) {
            avancar();
            String valor = tabela.getLiteral(t.getTableIndex()).getValue();
            return new Node("Literal (" + tp + "): " + valor);
        }

        if (ehPrimitivoEm(pos)) {                          // int.class, int[]::new
            return new Node("Tipo: " + tipoTexto());
        }

        if (ehSimbolo("this") || ehSimbolo("super")) {
            String v = t.getValue();
            avancar();
            return new Node(v);
        }

        if (consumirSe("new")) {
            return criacao();
        }

        if (ehSimbolo("switch")) {
            return switchCorpo();
        }

        if (ehSimbolo("(")) {
            avancar();
            Node interno = expressao();
            esperar(")");
            return interno;
        }

        throw erro("uma expressão");
    }

    // Depois de "new": tipo + (args [corpo anônimo] | dimensões [inicializador])
    private Node criacao() {
        Node no = new Node("New");
        if (ehSimbolo("<")) {
            argumentosDeTipo();
        }
        no.add(new Node("Tipo: " + tipoBase()));
        if (ehSimbolo("[")) {
            Node dims = new Node("Dimensoes");
            while (ehSimbolo("[")) {
                avancar();
                if (ehSimbolo("]")) {
                    dims.add(new Node("[]"));
                } else {
                    dims.add(expressao());
                }
                esperar("]");
            }
            no.add(dims);
            if (ehSimbolo("{")) {
                no.add(inicializadorArray());
            }
        } else if (ehSimbolo("(")) {
            no.add(argumentos());
            if (ehSimbolo("{")) {
                Node corpo = new Node("ClasseAnonima");
                corpoClasse(corpo);
                no.add(corpo);
            }
        } else {
            throw erro("'(' ou '['");
        }
        return no;
    }

    private Node argumentos() {
        Node no = new Node("Argumentos");
        esperar("(");
        if (!ehSimbolo(")")) {
            do {
                no.add(expressao());
            } while (consumirSe(","));
        }
        esperar(")");
        return no;
    }

    // ---------- lambda ----------

    private boolean ehLambda() {
        if (ehIdent(pos) && ehSimboloEm(pos + 1, "->")) {
            return true;
        }
        if (ehSimbolo("(")) {
            int f = fechamentoParenteses(pos);
            return f >= 0 && ehSimboloEm(f + 1, "->");
        }
        return false;
    }

    private Node lambda() {
        Node no = new Node("Lambda");
        Node ps = new Node("Parametros");
        if (ehIdent(pos)) {
            ps.add(new Node("Nome: " + esperarIdentificador()));
        } else {
            esperar("(");
            if (!ehSimbolo(")")) {
                do {
                    if (ehIdent(pos) && (ehSimboloEm(pos + 1, ",") || ehSimboloEm(pos + 1, ")"))) {
                        ps.add(new Node("Nome: " + esperarIdentificador()));
                    } else {
                        Node p = new Node("Parametro");
                        p.add(modificadores());
                        p.add(tipo());
                        if (consumirSe("...")) {
                            p.add(new Node("Varargs"));
                        }
                        p.add(new Node("Nome: " + esperarIdentificador()));
                        ps.add(p);
                    }
                } while (consumirSe(","));
            }
            esperar(")");
        }
        no.add(ps);
        esperar("->");
        no.add(ehSimbolo("{") ? bloco() : expressao());
        return no;
    }

    // "(" começa um cast?  (int) x   (String) obj   (A & B) x
    private boolean ehCast() {
        int i = pos + 1;
        if (ehPrimitivoEm(i)) {
            int j = skipType(i);
            return j > 0 && ehSimboloEm(j, ")");
        }
        int j = skipType(i);
        if (j < 0) {
            return false;
        }
        while (ehSimboloEm(j, "&")) {
            j = skipType(j + 1);
            if (j < 0) {
                return false;
            }
        }
        if (!ehSimboloEm(j, ")")) {
            return false;
        }
        Token n = tokenEm(j + 1);
        String v = n.getValue();
        switch (n.getType()) {
            case IDENTIFICADOR: case NUMERAL: case STRING:
            case CARACTERE: case BOOLEANO: case NULO:
                return true;
            case PALAVRA_CHAVE:
                return v.equals("this") || v.equals("super") || v.equals("new")
                        || v.equals("switch") || TIPOS_PRIMITIVOS.contains(v);
            case OPERADOR:
                return v.equals("!") || v.equals("~");
            case DELIMITADOR:
                return v.equals("(");
            default:
                return false;
        }
    }

    // ===================== LOOKAHEAD (olha à frente sem mover "pos") =====================

    // Se em i começa um tipo, devolve o índice logo após ele; senão, -1.
    private int skipType(int i) {
        if (ehPrimitivoEm(i)) {
            i++;
        } else if (ehIdent(i)) {
            i++;
            while (true) {
                if (ehSimboloEm(i, "<")) {
                    i = skipArgs(i);
                    if (i < 0) {
                        return -1;
                    }
                }
                if (ehSimboloEm(i, ".") && ehIdent(i + 1)) {
                    i += 2;
                } else {
                    break;
                }
            }
        } else {
            return -1;
        }
        while (ehSimboloEm(i, "[") && ehSimboloEm(i + 1, "]")) {
            i += 2;
        }
        return i;
    }

    // i está em "<": devolve o índice após o ">" correspondente, ou -1 se não for generics.
    private int skipArgs(int i) {
        int prof = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            String v = t.getValue();
            boolean op = t.getType() == TokenType.OPERADOR;
            if (op && v.equals("<")) {
                prof++;
            } else if (op && v.equals(">")) {
                prof--;
            } else if (op && v.equals(">>")) {
                prof -= 2;
            } else if (op && v.equals(">>>")) {
                prof -= 3;
            } else if (t.getType() == TokenType.IDENTIFICADOR || ehPrimitivoEm(i)
                    || ehSimboloEm(i, ",") || ehSimboloEm(i, ".") || ehSimboloEm(i, "?")
                    || ehSimboloEm(i, "[") || ehSimboloEm(i, "]") || ehSimboloEm(i, "&")
                    || ehSimboloEm(i, "extends") || ehSimboloEm(i, "super")) {
                // faz parte de um tipo genérico
            } else {
                return -1;
            }
            i++;
            if (prof <= 0) {
                return prof == 0 ? i : -1;
            }
        }
        return -1;
    }

    // Pula "final", "static"... a partir de i; devolve o novo índice.
    private int pularModificadoresEm(int i) {
        while (tokenEm(i).getType() == TokenType.PALAVRA_CHAVE
                && MODIFICADORES.contains(tokenEm(i).getValue())) {
            i++;
        }
        return i;
    }

    // i está em "(": devolve o índice do ")" correspondente, ou -1.
    private int fechamentoParenteses(int i) {
        int prof = 0;
        for (int k = i; k < tokens.size(); k++) {
            if (ehSimboloEm(k, "(")) {
                prof++;
            } else if (ehSimboloEm(k, ")")) {
                prof--;
                if (prof == 0) {
                    return k;
                }
            }
        }
        return -1;
    }

    // ===================== FERRAMENTAS AUXILIARES =====================

    private Token tokenEm(int i) {
        if (i >= 0 && i < tokens.size()) {
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

    private Token atual() {
        return tokenEm(pos);
    }

    private void avancar() {
        if (!fim()) {
            pos++;
        }
    }

    private boolean fim() {
        return pos >= tokens.size();
    }

    private boolean ehSimboloEm(int i, String valor) {
        Token t = tokenEm(i);
        TokenType tp = t.getType();
        return (tp == TokenType.PALAVRA_CHAVE
                || tp == TokenType.OPERADOR
                || tp == TokenType.DELIMITADOR)
                && t.getValue().equals(valor);
    }

    private boolean ehSimbolo(String valor) {
        return ehSimboloEm(pos, valor);
    }

    private boolean ehPrimitivoEm(int i) {
        Token t = tokenEm(i);
        return t.getType() == TokenType.PALAVRA_CHAVE
                && TIPOS_PRIMITIVOS.contains(t.getValue());
    }

    private boolean ehIdent(int i) {
        return tokenEm(i).getType() == TokenType.IDENTIFICADOR;
    }

    private boolean consumirSe(String valor) {
        if (ehSimbolo(valor)) {
            avancar();
            return true;
        }
        return false;
    }

    private void esperar(String valor) {
        if (!ehSimbolo(valor)) {
            throw erro("'" + valor + "'");
        }
        avancar();
    }

    private String esperarIdentificador() {
        if (atual().getType() != TokenType.IDENTIFICADOR) {
            throw erro("um identificador");
        }
        String nome = atual().getValue();
        avancar();
        return nome;
    }

    private RuntimeException erro(String esperado) {
        Token t = atual();
        return new RuntimeException(
                "Erro sintático na linha " + t.getLine()
                        + ", coluna " + t.getColumn());
    }
}
