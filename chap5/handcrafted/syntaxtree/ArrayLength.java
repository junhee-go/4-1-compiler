package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class ArrayLength extends Exp {
  public Exp e;
  
  public ArrayLength(Exp ae, int line, int column, int endLine, int endColumn) {
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
