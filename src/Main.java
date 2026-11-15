import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        String codigo;

        try {
            if (args.length > 0) {
                // Lê o arquivo Java informado como argumento.
                codigo = Files.readString(Path.of(args[0]));

                System.out.println(
                        "Arquivo analisado: " + args[0]
                );

            } else {
                // Se nenhum arquivo for informado, usa um exemplo.
                codigo = """
                        public class Pessoa {
                            // Dados da pessoa
                            private int idade = 20;
                            private String nome = "Ana";

                            public void apresentar() {
                                System.out.println(nome);
                                idade = idade + 1;
                            }
                        }
                        """;
            }

            executarAnalise(codigo);

        } catch (Exception e) {
            // Mostra a mensagem do erro.
            System.err.println("Erro ao analisar o código: " + e.getMessage());
        }
    }

    private static void executarAnalise(String codigo) {

        Lexer lexer = new Lexer(codigo);

        List<Token> tokens = lexer.tokenize();

        // 1. Lista de tokens
        System.out.println("\n===== LISTA DE TOKENS =====");

        for (int i = 0; i < tokens.size(); i++) {

            Token token = tokens.get(i);

            System.out.printf(
                    "Posição: %-4d | Tipo: %-16s | Valor: %-15s " +
                            "| Linha: %-4d | Coluna: %-4d | Índice: %d%n",
                    i,
                    token.getType(),
                    token.getValue(),
                    token.getLine(),
                    token.getColumn(),
                    token.getTableIndex()
            );
        }

        // 2. Tabela de símbolos
        System.out.println("\n===== TABELA DE SÍMBOLOS =====");

        List<Symbol> symbols =
                lexer.getSymbolTable().getSymbols();

        for (Symbol symbol : symbols) {
            System.out.println(symbol);
        }

        // 3. Tabela de literais
        System.out.println("\n===== TABELA DE LITERAIS =====");

        List<Literal> literals =
                lexer.getSymbolTable().getLiterals();

        for (Literal literal : literals) {

            System.out.printf(
                    "Índice: %-4d | Tipo: %-12s | Valor original: %s%n",
                    literal.getIndex(),
                    literal.getType(),
                    literal.getValue()
            );
        }

        // 4. Análise sintática
         System.out.println("\n===== ANÁLISE SINTÁTICA =====");
        Parser parser = new Parser(tokens, lexer.getSymbolTable());
        Node ast = parser.parse();
        System.out.println("Sintaxe correta!");

        System.out.println("-Árvore Sintática-");
        ast.print();

        // 5. Análise semântica
        SemanticAnalyzer semanticAnalyzer =
                new SemanticAnalyzer(tokens);

        semanticAnalyzer.exibirResultado();


        System.out.println("\n===== FIM DA ANÁLISE =====");
        System.out.println("Total de tokens: " + tokens.size());
        System.out.println("Total de símbolos: " + symbols.size());
        System.out.println("Total de literais: " + literals.size());
    }
}
