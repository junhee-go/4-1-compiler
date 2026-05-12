package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public abstract class Exp extends ASTNode {
  public abstract void accept(Visitor v);
  public abstract Type accept(TypeVisitor v);

  protected Exp(int line, int column) {
    super(line, column);
  }
}
