package Mips;

import Temp.Temp;

public class InReg implements Access {
  public final Temp temp;

  public InReg(Temp t) {
    this.temp = t;
  }
}
