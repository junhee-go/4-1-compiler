package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class This extends Exp {
  public This(int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
