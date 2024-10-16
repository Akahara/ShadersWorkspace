package wonder.shaderdisplay.scene;

public class ComputeDispatchCount {

    public final int x, y, z;

    public ComputeDispatchCount(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ComputeDispatchCount(int[] count) {
        this(count[0], count[1], count[2]);
    }
}
