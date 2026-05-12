package syntaxtree;

public abstract class ASTNode {
    public int line;
    public int column;

    public ASTNode(int line, int column) {
        this.line = line;
        this.column = column;
    }
}