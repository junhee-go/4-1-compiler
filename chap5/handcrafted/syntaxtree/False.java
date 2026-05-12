package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class False extends Exp {
  public False(int line, int column) {
    super(line, column);
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
