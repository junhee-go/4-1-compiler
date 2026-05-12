package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class Assign extends Statement {
  public Identifier i;
  public Exp e;

  public Assign(Identifier ai, Exp ae, int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
    i=ai; e=ae;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
