package wonder.shaderdisplay.display;

import org.lwjgl.assimp.*;
import wonder.shaderdisplay.Main;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {

    private final File sourceFile;
    private final Topology topology;
    private final int vbo, ibo, vao;
    private final int indexCount;

    public Mesh(File sourceFile, float[] vertexData, int[] indices, Topology topology) {
        this.sourceFile = sourceFile;
        this.indexCount = indices.length;
        this.topology = topology;

        if (indices.length % topology.vertexCount != 0)
            throw new IllegalArgumentException("Invalid number of indices, expected a multiple of " + topology.vertexCount);

        ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(vertexData);
        ByteBuffer shaderStorageIndicesData  = BufferUtils.fromInts(indices);

        this.vao = glGenVertexArrays();
        glBindVertexArray(vao);

        this.vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, shaderStorageVerticesData, GL_DYNAMIC_DRAW);

        this.ibo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndicesData, GL_DYNAMIC_DRAW);

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

    private Mesh() {
        this.sourceFile = null;
        this.ibo = 0;
        this.vao = 0;
        this.vbo = 0;
        this.indexCount = 0;
        this.topology = Topology.TRIANGLE_LIST;
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
        List<Integer> indexData = new ArrayList<>();

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
                        if (face.mNumIndices() != 3)
                            throw new IOException("Found a face with " + face.mIndices() + " indices?");
                        for (int j = 0; j < face.mNumIndices(); j++)
                            indexData.add(meshIndexOffset + face.mIndices().get(j));
                    }
                    meshIndexOffset += meshVertexCount;
                }
            }
        }
        float[] rawVertexData = new float[vertexData.size()];
        for (int i = 0; i < vertexData.size(); i++) rawVertexData[i] = vertexData.get(i);
        int[] rawIndexData = indexData.stream().mapToInt(i -> i).toArray();

        Main.logger.info("Successfully loaded mesh " + file);
        return new Mesh(file, rawVertexData, rawIndexData, Topology.TRIANGLE_LIST);
    }

    public static Mesh emptyMesh() {
        return new Mesh();
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void dispose() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ibo);
        glDeleteVertexArrays(vao);
    }

    public void makeDrawCall() {
        glBindVertexArray(vao);
        glDrawElements(topology.glType, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }
}
