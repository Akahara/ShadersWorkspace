package wonder.shaderdisplay.display;

import wonder.shaderdisplay.scene.SSBOBinding;

public class IndirectDrawDescription {

    public final SSBOBinding indirectArgsBuffer;
    public final String vertexBufferName;
    public final String indexBufferName;
    public final int indirectCallsCount;
    public final Mesh.Topology topology;

    public IndirectDrawDescription(SSBOBinding indirectArgsBuffer, String vertexBuffer, String indexBuffer, int indirectCallsCount, Mesh.Topology topology) {
        this.indirectArgsBuffer = indirectArgsBuffer;
        this.vertexBufferName = vertexBuffer;
        this.indexBufferName = indexBuffer;
        this.indirectCallsCount = indirectCallsCount;
        this.topology = topology;
    }

}
