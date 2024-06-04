import sys
import math

grid_size = 50 if len(sys.argv) <= 1 else int(sys.argv[1])
normalized = "normalized" in sys.argv
centered = "centered" in sys.argv

ux = 1
uy = 0
vx = 1/2
vy = math.sqrt(3)/2
scale = 1 if not normalized else 1/(grid_size+.5)

print("vn 0 0 1")

N = grid_size
M = int(grid_size / vy)

for j in range(0, M):
    for i in range(0, N):
        x = ((i - j//2)*ux+j*vx) * scale
        y = ((i - j//2)*uy+j*vy) * scale
        if centered:
            x -= grid_size * scale * .5
            y -= grid_size * scale * .5
        print(f"v {x} {y} 0")
        
for j in range(0, M):
    for i in range(0, N):
        x = ((i - j//2)*ux+j*vx) / grid_size
        y = ((i - j//2)*uy+j*vy) / grid_size
        print(f"vt {x} {y}")
        
for j in range(0, M-1):
    for i in range(0, N-1):
        k = i + j*N
        v0 = 1 + k
        v1 = 1 + k+1
        v2 = 1 + k+N
        v3 = 1 + k+N+1
        if j%2 == 0:
            print(f"f {v0}/{v0}/1 {v1}/{v1}/1 {v2}/{v2}/1")
            print(f"f {v2}/{v2}/1 {v1}/{v1}/1 {v3}/{v3}/1")
        else:
            print(f"f {v0}/{v0}/1 {v3}/{v3}/1 {v2}/{v2}/1")
            print(f"f {v0}/{v0}/1 {v1}/{v1}/1 {v3}/{v3}/1")
        
