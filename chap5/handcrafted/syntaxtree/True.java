package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class True extends Exp {
  public True(int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
  }
  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
