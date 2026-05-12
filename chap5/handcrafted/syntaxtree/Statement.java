package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public abstract class Statement extends ASTNode {
  protected Statement(int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
  }

  public abstract void accept(Visitor v);
  public abstract Type accept(TypeVisitor v);
}
