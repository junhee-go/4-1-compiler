class Test1{
    public static void main(String[] a){
	    System.out.println(new Simple().add());
    }
}

class Simple {
    public int add() {
        int x;
        int y;
        int z;
        x = 10;
        y = 20;
        z = x + y;
        return z;
    }
}