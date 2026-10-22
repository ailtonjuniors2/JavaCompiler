
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SymbolTable {

    // Armazena os símbolos pelo nome, evitando cadastrar o mesmo identificador novamente.
    // O LinkedHashMap preserva a ordem em que os símbolos foram inseridos.
    private final Map<String, Symbol> symbols =
            new LinkedHashMap<>();

    // Permite localizar literais usando uma chave formada pelo tipo e pelo valor.
    private final Map<String, Literal> literalMap =
            new LinkedHashMap<>();

    // Armazena os literais na ordem de cadastro, permitindo acessá-los pelo índice.
    private final List<Literal> literals =
            new ArrayList<>();

    // Registra um símbolo novo ou retorna o índice de um símbolo já existente.
    public int addSymbol(String name, TokenType type) {

        // Verifica se o símbolo já foi registrado.
        Symbol existing = symbols.get(name);

        // Se já existir, reutiliza o índice da entrada existente.
        if (existing != null) {
            return existing.getIndex();
        }

        // Define o próximo índice com base na quantidade de símbolos cadastrados.
        int index = symbols.size();

        // Cria o objeto que representa o símbolo.
        Symbol symbol = new Symbol(index, name, type);

        // Associa o nome à entrada criada na tabela.
        symbols.put(name, symbol);

        // Retorna o índice para que o token possa referenciar o símbolo.
        return index;
    }

    // Registra um literal novo ou retorna o índice de um literal já existente.
    public int addLiteral(String value, TokenType type) {

        // Combina tipo e valor para distinguir literais de categorias diferentes.
        String key = type.name() + ":" + value;

        // Verifica se o mesmo literal já foi registrado.
        Literal existing = literalMap.get(key);

        // Se já existir, reutiliza seu índice.
        if (existing != null) {
            return existing.getIndex();
        }

        // Define o próximo índice da tabela de literais.
        int index = literals.size();

        // Cria o objeto que representa o literal.
        Literal literal = new Literal(index, type, value);

        // Registra o literal no mapa para permitir consultas posteriores.
        literalMap.put(key, literal);

        // Adiciona o literal à lista que mantém a ordem dos registros.
        literals.add(literal);

        // Retorna o índice que será associado ao token.
        return index;
    }

    // Retorna uma lista com os símbolos cadastrados.
    public List<Symbol> getSymbols() {

        // Devolve uma cópia da coleção, sem expor diretamente o mapa interno.
        return new ArrayList<>(symbols.values());
    }

    // Retorna uma lista com os literais cadastrados.
    public List<Literal> getLiterals() {

        // Devolve uma cópia da lista interna de literais.
        return new ArrayList<>(literals);
    }

    // Recupera um literal pelo índice correspondente na lista.
    public Literal getLiteral(int index) {

        // Retorna o literal da posição informada.
        return literals.get(index);
    }
}
