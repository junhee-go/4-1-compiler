package syntaxtree;

public abstract class ASTNode {
    public int line;
    public int column;
    public int endLine;
    public int endColumn;

    public ASTNode(int line, int column, int endLine, int endColumn) {
        this.line = line;
        this.column = column;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }
}
