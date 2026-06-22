class Test2{
    public static void main(String[] a){
	    System.out.println(new Simple().add(10,20));
    }
}

class Simple {
    public int add(int x, int y) {
        int z;
        z = x + y;
        return z;
    }
}