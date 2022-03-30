#!/usr/bin/python3

from random import random
from scipy.constants import golden
import numpy as np

def rand():
	return round(random()*2-1, 3)

def length(pt):
	return np.linalg.norm(pt)

def printPoint(npArray):
	print(*npArray, 1)
	
def printEdge(edge):
	printPoint(edge[0])
	printPoint(edge[1])

def printIcosahedronPoints():
	print("points")
	pt = [1, golden, 0]
	for signs in range(4):
		g = pt[:]
		g[0] = ((signs% 2)*2-1) * g[0]
		g[1] = ((signs//2)*2-1) * g[1]
		for swap in range(3):
			g.append(g[0])
			del g[0]
			print(*g, 1)

def genIcosahedronVertices():
	points = []
	pt = [1, golden, 0]
	for signs in range(4):
		g = pt[:]
		g[0] = ((signs% 2)*2-1) * g[0]
		g[1] = ((signs//2)*2-1) * g[1]
		for swap in range(3):
			g.append(g[0])
			del g[0]
			points.append(np.array(g))
	return points
	
def findFaces(points, maxDist):
	faces = []
	for (i, p1) in enumerate(points):
		for (j, p2) in enumerate(points[i+1:]):
			if length(p1-p2) < maxDist:
				for p3 in points[j+1:]:
					if length(p2-p3) < maxDist and length(p1-p3) < maxDist:
						faces.append((p1, p2, p3))
	return faces
	
def splitIcosahedronFaces(faces):
	newFaces = []
	for (p1, p2, p3) in faces:
		p4 = (p1+p2)/2; p4 = p4/length(p4)
		p5 = (p2+p3)/2; p5 = p5/length(p5)
		p6 = (p3+p1)/2; p6 = p6/length(p6)
		newFaces.append((p1, p4, p6))
		newFaces.append((p2, p5, p4))
		newFaces.append((p3, p6, p5))
		newFaces.append((p4, p5, p6))
	return newFaces
	
def addUniqueEdge(edges, edge):
	p1, p2 = edge
	for (pp1, pp2) in edges:
		if (np.array_equiv(pp1, p1) and np.array_equiv(pp2, p2)) or (np.array_equiv(pp1, p2) and np.array_equiv(pp2, p1)):
			return
	edges.append(edge)
	
def getAllEdges(faces):
	edges = []
	for (p1, p2, p3) in faces:
		addUniqueEdge(edges, (p1, p2))
		addUniqueEdge(edges, (p2, p3))
		addUniqueEdge(edges, (p3, p1))
	return edges

def printIcosahedron():
	print("lines")
	points = genIcosahedronVertices()
	points = list(map(lambda pt: pt/length(pt), points))
	faces = findFaces(points, 1.3)
	for i in range(2):
		faces = splitIcosahedronFaces(faces)
	edges = getAllEdges(faces)
	for edge in edges:
		printEdge(edge)

			
def printRandomPoints():
	print("points")
	for i in range(100):
		print(rand(), rand(), 0, 1)

printIcosahedron()

#print("lines")
#print(-1, -1, 0, 1)
#print(+1, +1, 0, 1)
