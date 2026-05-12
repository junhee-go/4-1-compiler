package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class While extends Statement {
  public Exp e;
  public Statement s;

  public While(Exp ae, Statement as, int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
    e=ae; s=as;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
