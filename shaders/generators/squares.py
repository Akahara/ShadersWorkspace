import sys
import math

square_count = 50 if len(sys.argv) <= 1 else int(sys.argv[1])
xymin = 0 if "centered" not in sys.argv else -1

"""
3-4
| |
1-2
"""

print("vn 0 0 1")
print(f"v {xymin} {xymin} 0")
print(f"vt 0 0")
print(f"v {xymin} 1 0")
print(f"vt 0 1")
print(f"v 1 {xymin} 0")
print(f"vt 1 0")
print(f"v 1 1 0")
print(f"vt 1 1")


for i in range(1, square_count+1):
    print(f"vn 0 0 {i}")
    print(f"f 1/1/{i} 2/2/{i} 3/3/{i}")
    print(f"f 3/3/{i} 2/2/{i} 4/4/{i}")
    