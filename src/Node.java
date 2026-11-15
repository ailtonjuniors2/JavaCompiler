import java.util.ArrayList;
import java.util.List;

public class Node {

    private final String label;                          // texto do nó (ex.: "If", "Binario (+)")
    private final List<Node> children = new ArrayList<>(); // filhos do nó

    public Node(String label) {
        this.label = label;
    }

    // Adiciona um filho (ignora null) e devolve o próprio nó, permitindo encadear.
    public Node add(Node child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    // Imprime a árvore a partir deste nó.
    public void print() {
        System.out.println(label);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).print("", i == children.size() - 1);
        }
    }

    // Imprime este nó com o prefixo de desenho e depois seus filhos.
    private void print(String prefix, boolean last) {
        System.out.println(prefix + (last ? "-- " : "|-- ") + label);
        String novoPrefixo = prefix + (last ? "    " : "|   ");
        for (int i = 0; i < children.size(); i++) {
            children.get(i).print(novoPrefixo, i == children.size() - 1);
        }
    }
}
