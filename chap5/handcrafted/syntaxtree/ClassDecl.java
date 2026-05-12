package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public abstract class ClassDecl extends ASTNode {
  protected ClassDecl(int line, int column) {
    super(line, column);
  }

  public abstract void accept(Visitor v);
  public abstract Type accept(TypeVisitor v);
}
