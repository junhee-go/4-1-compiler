package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class IntegerType extends Type {
  // 파싱된 소스코드의 타입 정보를 표현하기 위한 생성자
  public IntegerType(int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
  }

  // 연산 결과 타입 표현용 생성자 => 소스 코드가 아닌, 소스 코드의 연산 결과에 대한 타입 표현용이기에 위치 정보가 없음 -> -1로 초기화
  public IntegerType() {
    super(-1, -1, -1, -1);
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
