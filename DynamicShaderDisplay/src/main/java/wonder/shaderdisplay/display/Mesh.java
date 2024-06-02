package wonder.shaderdisplay.display;

import org.lwjgl.assimp.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Mesh {

    private final int vbo, ibo, vao;
    private final int indexCount;

    public Mesh(float[] vertexData, int[] indices) {
        indexCount = indices.length;

        ByteBuffer shaderStorageVerticesData = BufferUtils.fromFloats(vertexData);
        ByteBuffer shaderStorageIndicesData  = BufferUtils.fromInts(indices);

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, shaderStorageVerticesData, GL_DYNAMIC_DRAW);

        ibo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, shaderStorageIndicesData, GL_DYNAMIC_DRAW);

        // fixed vertex layout: [vec4]
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, NULL);

        glBindVertexArray(0);
    }

    public static Mesh fullscreenTriangle() {
        return new Mesh(new float[] {
                -1,-1,0,1,
                +3,-1,0,1,
                -1,+3,0,1
        }, new int[] {
                0,1,2
        });
    }

    public static Mesh parseFile(File file) throws IOException {
        List<Float> vertexData = new ArrayList<>();
        List<Integer> indexData = new ArrayList<>();
        try (AIScene scene = Assimp.aiImportFile(file.getAbsolutePath(), aiProcess_JoinIdenticalVertices | aiProcess_Triangulate)) {
            if (scene == null)
                throw new IOException("Could not load mesh file");
            for (int i = 0; i < scene.mNumMeshes(); i++) {
                int meshIndexOffset = vertexData.size() / 3;
                try (AIMesh mesh = AIMesh.create(scene.mMeshes().get(i))) {
                    for (AIVector3D aiVertex : mesh.mVertices()) {
                        vertexData.add(aiVertex.x());
                        vertexData.add(aiVertex.y());
                        vertexData.add(aiVertex.z());
                    }
                    for (AIFace face : mesh.mFaces()) {
                        if (face.mNumIndices() != 3)
                            throw new IOException("Found a face with " + face.mIndices() + " indices?");
                        for (int j = 0; j < face.mNumIndices(); j++)
                            indexData.add(meshIndexOffset + face.mIndices().get(j));
                    }
                }
            }
        }
        float[] rawVertexData = new float[vertexData.size()];
        for (int i = 0; i < vertexData.size(); i++) rawVertexData[i] = vertexData.get(i);
        int[] rawIndexData = indexData.stream().mapToInt(i -> i).toArray();
        return new Mesh(rawVertexData, rawIndexData);
    }

    public void dispose() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ibo);
        glDeleteVertexArrays(vao);
    }

    public void makeDrawCall() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }
}
