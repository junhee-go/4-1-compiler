package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class Not extends Exp {
  public Exp e;
  
  public Not(Exp ae, int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
    e=ae;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
