class Test3{
    public static void main(String[] a){
	    System.out.println(new Simple().max(10,20));
    }
}

class Simple {
    public int max(int x, int y) {
        int z;
        if (x<y) z = y;
        else z = x;
        return z;
    }
}