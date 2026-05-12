package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public abstract class Type extends ASTNode {
  protected Type(int line, int column) {
    super(line, column);
  }

  public abstract void accept(Visitor v);
  public abstract Type accept(TypeVisitor v);
}
