package wonder.shaderdisplay.display;

import com.fasterxml.jackson.annotation.JsonValue;
import org.lwjgl.assimp.*;
import wonder.shaderdisplay.Main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {

    private final File sourceFile;
    private final int vbo;
    private final int triangleIbo, lineIbo;
    private final int vao;
    private final int triangleIndexCount, lineVertexCount;

    public Mesh(File sourceFile, float[] vertexData, int[] triangleIndices, int[] lineIndices) {
        this.sourceFile = sourceFile;

        if (triangleIndices != null && triangleIndices.length % 3 != 0)
            throw new IllegalArgumentException("Invalid number of indices, expected a multiple of 3");
        if (lineIndices != null && lineIndices.length % 2 != 0)
            throw new IllegalArgumentException("Invalid number of indices, expected a multiple of 2");

        this.vao = glGenVertexArrays();
        glBindVertexArray(vao);

        this.vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, BufferUtils.fromFloats(vertexData), GL_DYNAMIC_DRAW);

        if (triangleIndices != null) {
            this.triangleIndexCount = triangleIndices.length;
            this.triangleIbo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, triangleIbo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, BufferUtils.fromInts(triangleIndices), GL_DYNAMIC_DRAW);
        } else {
            this.triangleIndexCount = 0;
            this.triangleIbo = 0;
        }

        if (lineIndices != null) {
            this.lineVertexCount = lineIndices.length;
            this.lineIbo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, lineIbo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, BufferUtils.fromInts(lineIndices), GL_DYNAMIC_DRAW);
        } else {
            this.lineVertexCount = 0;
            this.lineIbo = 0;
        }

        // fixed vertex layout: [vec4 position, vec3 normal, vec2 uv]
        int stride = 4+3+2;
        if (vertexData.length % stride != 0)
            throw new IllegalArgumentException("Invalid vertex data, check vertex format");
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, stride*4, 4*0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride*4, 4*4);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride*4, 4*(4+3));
        glBindVertexArray(0);
    }

    public Mesh(File sourceFile, float[] vertexData, int[] indices, Topology topology) {
        this(sourceFile, vertexData, topology == Topology.TRIANGLE_LIST ? indices : null, topology == Topology.LINE_LIST ? indices : null);
    }

    private Mesh() {
        this.sourceFile = null;
        this.triangleIbo = 0;
        this.lineIbo = 0;
        this.vao = 0;
        this.vbo = 0;
        this.triangleIndexCount = 0;
        this.lineVertexCount = 0;
    }

    public enum Topology {
        TRIANGLE_LIST(3, GL_TRIANGLES),
        LINE_LIST(2, GL_LINES);

        final int vertexCount;
        final int glType;

        Topology(int vertexCount, int glType) {
            this.vertexCount = vertexCount;
            this.glType = glType;
        }

        @JsonValue
        public String serialName() { return name().toLowerCase(); }
    }

    public static Mesh makeFullscreenTriangleMesh() {
        return new Mesh(
            null,
            new float[] {
                -1,-1,0,1, 0,0,0, 0,0,
                +3,-1,0,1, 0,0,0, 2,0,
                -1,+3,0,1, 0,0,0, 0,2,
            },
            new int[] { 0,1,2 },
            Topology.TRIANGLE_LIST
        );
    }

    public static Mesh makeLineMesh() {
        return new Mesh(
            null,
            new float[] {
                0,0,0,1, 0,0,0, 0,0,
                1,0,0,1, 0,0,0, 1,0,
            },
            new int[] { 0, 1 },
            Topology.LINE_LIST
        );
    }

    public static Mesh parseFile(File file) throws IOException {
        List<Float> vertexData = new ArrayList<>();
        List<Integer> triangleIndexData = new ArrayList<>();
        List<Integer> lineIndexData = new ArrayList<>();

        int meshIndexOffset = 0;
        try (AIScene scene = Assimp.aiImportFile(file.getAbsolutePath(), aiProcess_JoinIdenticalVertices | aiProcess_Triangulate)) {
            if (scene == null)
                throw new IOException("Could not load mesh file");
            int numMeshes = scene.mNumMeshes();
            if (numMeshes == 0)
                Main.logger.warn("File '" + file + "' contains 0 meshes");
            for (int i = 0; i < numMeshes; i++) {
                try (AIMesh mesh = AIMesh.create(scene.mMeshes().get(i))) {
                    AIVector3D.Buffer vertices = mesh.mVertices();
                    AIVector3D.Buffer uvs = mesh.mTextureCoords(0);
                    AIVector3D.Buffer normals = mesh.mNormals();
                    int meshVertexCount = 0;
                    while (vertices.hasRemaining()) {
                        meshVertexCount++;
                        // [vec4 position]
                        AIVector3D aiVertex = vertices.get();
                        vertexData.add(aiVertex.x());
                        vertexData.add(aiVertex.y());
                        vertexData.add(aiVertex.z());
                        vertexData.add(1.f);
                        // [vec3 normal]
                        if (normals != null) {
                            AIVector3D aiNormal = normals.get();
                            vertexData.add(aiNormal.x());
                            vertexData.add(aiNormal.y());
                            vertexData.add(aiNormal.z());
                        } else {
                            vertexData.add(0.f);
                            vertexData.add(0.f);
                            vertexData.add(0.f);
                        }
                        // [vec2 uv]
                        if (uvs != null) {
                            AIVector3D aiUV = uvs.get();
                            vertexData.add(aiUV.x());
                            vertexData.add(aiUV.y());
                        } else {
                            vertexData.add(0.f);
                            vertexData.add(0.f);
                        }
                    }
                    for (AIFace face : mesh.mFaces()) {
                        List<Integer> indexData;
                        if (face.mNumIndices() == 3)
                            indexData = triangleIndexData;
                        else if (face.mNumIndices() == 2)
                            indexData = lineIndexData;
                        else
                            throw new IOException("Found a face with " + face.mNumIndices() + " indices?");
                        for (int j = 0; j < face.mNumIndices(); j++)
                            indexData.add(meshIndexOffset + face.mIndices().get(j));
                    }
                    meshIndexOffset += meshVertexCount;
                }
            }
        }
        float[] rawVertexData = new float[vertexData.size()];
        for (int i = 0; i < vertexData.size(); i++) rawVertexData[i] = vertexData.get(i);
        int[] rawTriangleIndexData = triangleIndexData.isEmpty() ? null : triangleIndexData.stream().mapToInt(i -> i).toArray();
        int[] rawLineIndexData = lineIndexData.isEmpty() ? null : lineIndexData.stream().mapToInt(i -> i).toArray();

        Main.logger.info("Successfully loaded mesh " + file);
        return new Mesh(file, rawVertexData, rawTriangleIndexData, rawLineIndexData);
    }

    public static Mesh emptyMesh() {
        return new Mesh();
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void dispose() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(triangleIbo);
        glDeleteVertexArrays(vao);
    }

    public void makeDrawCall() {
        glBindVertexArray(vao);
        if (triangleIbo != 0) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, triangleIbo);
            glDrawElements(GL_TRIANGLES, triangleIndexCount, GL_UNSIGNED_INT, 0);
        }
        if (lineVertexCount != 0) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, lineIbo);
            glDrawElements(GL_LINES, lineVertexCount, GL_UNSIGNED_INT, 0);
        }
        glBindVertexArray(0);
    }
}
