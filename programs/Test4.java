class Test4{
    public static void main(String[] a){
	    System.out.println(new Simple().sum(10,20));
    }
}

class Simple {
    public int sum(int x, int y) {
        int i;
        int z;
        i = x;
        z = 0;
        while (i<y+1) {
            z = z + i;
            i = i + 1;
        }
        return z;
    }
}