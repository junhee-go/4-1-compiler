package Mips;

import Assem.InstrList;
import Assem.OPER;
import Util.BoolList;
import Temp.Temp;
import Temp.TempList;
import Temp.TempMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Frame {
  public final String name;
  public final List<Access> formals = new ArrayList<>();
  private int frameSize = 0;
  private static final int WORD = 4;
  private static final int NUM_ARG_REGS = 4;
  private static final int ARG_BASE = 8; // positive offsets from $fp for stack-passed args
  private static final Map<Temp, String> TEMP_NAMES = new HashMap<>();

  public static final Temp ZERO = namedTemp("$zero");
  public static final Temp RV = namedTemp("$v0");
  public static final Temp RV2 = namedTemp("$v1");
  public static final Temp FP = namedTemp("$fp");
  public static final Temp SP = namedTemp("$sp");
  public static final Temp RA = namedTemp("$ra");

  private static final Temp[] ARG_REG_ARRAY = {
      namedTemp("$a0"),
      namedTemp("$a1"),
      namedTemp("$a2"),
      namedTemp("$a3")
  };

  private static final Temp[] CALLEE_SAVE_ARRAY = {
      namedTemp("$s0"), namedTemp("$s1"), namedTemp("$s2"), namedTemp("$s3"),
      namedTemp("$s4"), namedTemp("$s5"), namedTemp("$s6"), namedTemp("$s7")
  };

  private static final Temp[] CALLER_SAVE_ARRAY = {
      namedTemp("$t0"), namedTemp("$t1"), namedTemp("$t2"), namedTemp("$t3"),
      namedTemp("$t4"), namedTemp("$t5"), namedTemp("$t6"), namedTemp("$t7"),
      namedTemp("$t8"), namedTemp("$t9")
  };

  private static final Temp[] SPECIAL_REG_ARRAY = {
      ZERO, RV, RV2, FP, SP, RA
  };

  public Frame(String name, BoolList escapes) {
    this.name = name;
    // Keep 2 words reserved for saved $fp/$ra so allocLocal/formal frame slots
    // start at -12($fp), avoiding collision with -8/-4($fp).
    frameSize = 2 * WORD;
    int a = 0;
    for (BoolList p = escapes; p != null; p = p.tail, a++) {
      if (a < NUM_ARG_REGS) {
        // First NUM_ARG_REGS args come in registers $a0..$a3
        if (p.head) {
          // Escaping: place in frame (negative offset)
          frameSize += WORD;
          formals.add(new InFrame(-frameSize));
        } else {
          // Non-escaping: keep in a register (represented by a temp)
          formals.add(new InReg(new Temp()));
        }
      } else {
        // Args beyond NUM_ARG_REGS come in on the caller's stack: positive offsets from $fp
        int stackArgIndex = a - NUM_ARG_REGS;
        int offset = ARG_BASE + stackArgIndex * WORD;
        formals.add(new InFrame(offset));
      }
    }
  }

  public Access allocLocal(boolean escapes) {
    if (escapes) {
      frameSize += WORD;
      return new InFrame(-frameSize);
    } else {
      return new InReg(new Temp());
    }
  }

  public int frameSize() { return frameSize; }
  public List<Access> getFormals() { return formals; }

  public static int wordSize() { return WORD; }
  public static int numArgRegs() { return NUM_ARG_REGS; }
  public static Temp argReg(int index) { return ARG_REG_ARRAY[index]; }
  public static String printLabel() { return "_print"; }
  public static String errorLabel() { return "_error"; }
  public static String heapAllocLabel() { return "_heapAlloc"; }
  public static String allocArrayLabel() { return "_allocArray"; }

  public static TempList specialRegs() { return toTempList(SPECIAL_REG_ARRAY); }
  public static TempList argRegs() { return toTempList(ARG_REG_ARRAY); }
  public static TempList calleeSaves() { return toTempList(CALLEE_SAVE_ARRAY); }
  public static TempList callerSaves() { return toTempList(CALLER_SAVE_ARRAY); }

  private static TempList toTempList(Temp[] regs) {
    TempList list = null;
    for (int i = regs.length - 1; i >= 0; i--) {
      list = new TempList(regs[i], list);
    }
    return list;
  }

  private static Temp namedTemp(String name) {
    Temp t = new Temp();
    TEMP_NAMES.put(t, name);
    return t;
  }

  // TEMP_NAMES 매핑을 프린트하는 함수를 작성해주세요.
  public String tempMapString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Temp, String> entry : TEMP_NAMES.entrySet()) {
      sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
    }
    return sb.toString();
  }

  public String prologue() {
    int frameBytes = Math.max(2 * WORD, frameSize);
    StringBuilder sb = new StringBuilder();
    sb.append("addiu $sp, $sp, -").append(frameBytes).append("\n");
    sb.append("sw $ra, ").append(frameBytes - WORD).append("($sp)\n");
    sb.append("sw $fp, ").append(frameBytes - 2 * WORD).append("($sp)\n");
    sb.append("addiu $fp, $sp, ").append(frameBytes).append("\n");
    return sb.toString();
  }

  public String epilogue() {
    StringBuilder sb = new StringBuilder();
    if ("main".equals(name)) {
      sb.append("li $v0, 10\n");
      sb.append("syscall\n");
      return sb.toString();
    }
    sb.append("move $sp, $fp\n");
    sb.append("lw $fp, -8($sp)\n");
    sb.append("lw $ra, -4($sp)\n");
    sb.append("jr $ra\n");
    return sb.toString();
  }

  private static TempList sinkTemps() {
    TempList list = calleeSaves();
    list = new TempList(FP, list);
    list = new TempList(SP, list);
    list = new TempList(RA, list);
    list = new TempList(ZERO, list);
    return list;
  }

  private static InstrList append(InstrList a, InstrList b) {
    if (a == null) return b;
    InstrList headList = a;
    while (a.tail != null) a = a.tail;
    a.tail = b;
    return headList;
  }

  public Tree.Stm procEntryExit1(Tree.Stm body) {
    // Build IR prologue/epilogue around body: formal arg binding and callee-save save/restore.
    Tree.Stm entry = null;
    // View shift: Bind register arguments into formal accesses
    for (int i = 0; i < formals.size(); i++) {
      Access acc = formals.get(i);
      if (i < NUM_ARG_REGS) {
        Temp areg = ARG_REG_ARRAY[i];
        if (acc instanceof InReg) {
          Temp formal = ((InReg) acc).temp;
          entry = seq(entry, new Tree.MOVE(new Tree.TEMP(formal), new Tree.TEMP(areg)));
        } else if (acc instanceof InFrame) {
          int off = ((InFrame) acc).offset;
          Tree.Exp addr = new Tree.BINOP(Tree.BINOP.PLUS, new Tree.TEMP(FP), new Tree.CONST(off));
          entry = seq(entry, new Tree.MOVE(new Tree.MEM(addr), new Tree.TEMP(areg)));
        }
      } else {
        // Args beyond NUM_ARG_REGS are passed on caller's stack at positive offsets from $fp.
        // If a future implementation creates InReg for non-escaping formals here,
        // move MEM($fp+offset) into that temp. Current Frame uses InFrame, so already accessible.
        if (acc instanceof InReg) {
          // Not currently produced by Frame, but handle defensively.
          int stackIdx = i - NUM_ARG_REGS;
          int off = ARG_BASE + stackIdx * WORD;
          Tree.Exp addr = new Tree.BINOP(Tree.BINOP.PLUS, new Tree.TEMP(FP), new Tree.CONST(off));
          Temp formal = ((InReg) acc).temp;
          entry = seq(entry, new Tree.MOVE(new Tree.TEMP(formal), new Tree.MEM(addr)));
        }
      }
    }
 
    // 각 callee-save 레지스터마다 새로운 Temp를 만들고(Temp saved = new Temp(); 식),
    //  함수 진입 시점에 MOVE(TEMP(saved), TEMP(reg))을 entry 시퀀스에 넣어 둡니다.
    //  이때 (reg, saved) 쌍을 나중에 복구용으로 기록해 둡니다.
    // 아직 이 temp들을 메모리에 내보내지 않습니다. 이 temp들이 
    // callee-save 보관 위치를 논리적으로 나타내며, 실제로 프레임에 spill할지는 이후의 
    // spill-aware 레지스터 할당기가 필요에 따라 결정합니다.
    List<Temp> savedCalleeSaves = new ArrayList<>();
    for (Temp callee : CALLEE_SAVE_ARRAY) {
      Temp saved = new Temp();
      savedCalleeSaves.add(saved);
      entry = seq(entry, new Tree.MOVE(new Tree.TEMP(saved), new Tree.TEMP(callee)));
    }

    // Concatenate entry moves with body, ensuring the method entry LABEL comes first
    Tree.Stm withBody = seq (entry, body); // placeEntryAfterLabel(body, entry);

    // 에필로그 작성 시에는 저장된 (reg, saved) 목록을 역순으로 순회하면서 
    // MOVE(TEMP(reg), TEMP(saved))을 붙여 원래 레지스터 값을 복원합니다.
    Tree.Stm exit = null;
    for (int i = savedCalleeSaves.size() - 1; i >= 0; i--) {
      Temp saved = savedCalleeSaves.get(i);
      Temp callee = CALLEE_SAVE_ARRAY[i];
      exit = seq(exit, new Tree.MOVE(new Tree.TEMP(callee), new Tree.TEMP(saved)));
    }

    Tree.Stm finalTree = seq (withBody, exit);

    return finalTree;
  }

  // Helper: concatenate statements
  private static Tree.Stm seq(Tree.Stm a, Tree.Stm b) {
    if (a == null) return b;
    if (b == null) return a;
    return new Tree.SEQ(a, b);
  }

  public InstrList procEntryExit2(InstrList body) {
    InstrList sink = new InstrList(new OPER("", null, sinkTemps()), null);
    return append(body, sink);
  }

  public Proc procEntryExit3(InstrList body) {
    String prolog = name + ":\n" + prologue();
    String epilog = epilogue();
    return new Proc(prolog, body, epilog);
  }

  public static String standard_library() {
    return printLabel() + ":\n"
        + "  li $v0 1   # syscall: print integer\n"
        + "  syscall\n"
        + "  la $a0 _newline\n"
        + "  li $v0 4   # syscall: print string\n"
        + "  syscall\n"
        + "  jr $ra\n"
        + "\n"
        + errorLabel() + ":\n"
        + "  li $v0 4   # syscall: print string\n"
        + "  syscall\n"
        + "  li $v0 10  # syscall: exit\n"
        + "  syscall\n"
        + "\n"
        + heapAllocLabel() + ":\n"
        + "  li $v0 9   # syscall: sbrk\n"
        + "  syscall\n"
        + "  jr $ra\n"
        + "\n"
        + allocArrayLabel() + ":\n"
        + "  sw $fp, -8($sp)\n"
        + "  move $fp, $sp\n"
        + "  subu $sp, $sp, 8\n"
        + "  sw $ra, -4($fp)\n"
        + "  move $t0, $a0\n"
        + "  mul $t1, $t0, 4\n"
        + "  addu $t1, $t1, 4\n"
        + "  move $a0, $t1\n"
        + "  jal " + heapAllocLabel() + "\n"
        + "  move $t1, $v0\n"
        + "  sw $t0, 0($t1)\n"
        + "  li $t2, 0\n"
        + "  addiu $t3, $t1, 4\n"
        + allocArrayLabel() + "_init:\n"
        + "  slt $t4, $t2, $t0\n"
        + "  beqz $t4, " + allocArrayLabel() + "_done\n"
        + "  sw $zero, 0($t3)\n"
        + "  addiu $t3, $t3, 4\n"
        + "  addiu $t2, $t2, 1\n"
        + "  j " + allocArrayLabel() + "_init\n"
        + allocArrayLabel() + "_done:\n"
        + "  move $v0, $t1\n"
        + "  lw $ra, -4($fp)\n"
        + "  lw $fp, -8($fp)\n"
        + "  addu $sp, $sp, 8\n"
        + "  jr $ra\n"
        + "\n"
        + ".data\n"
        + ".align 0\n"
        + "_newline: .asciiz \"\\n\"\n"
        + "_str0: .asciiz \"null pointer\\n\"\n";
  }

  public String tempMap(Temp temp) {
    return TEMP_NAMES.getOrDefault(temp, null);
  }

  public static TempMap regNameMap() {
    return new TempMap() {
      @Override
      public String tempMap(Temp t) {
        return TEMP_NAMES.getOrDefault(t, null);
      }
    };
  }

  public static class Proc {
    public final String prolog;
    public final InstrList body;
    public final String epilog;

    public Proc(String prolog, InstrList body, String epilog) {
      this.prolog = prolog;
      this.body = body;
      this.epilog = epilog;
    }
  }
}
