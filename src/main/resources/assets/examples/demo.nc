; demo.nc - GRBL dialect sample exercising G0/G1/G2/G3, units and mode
; switches, and one deliberate out-of-bed move (G0 X350 on a 300 mm bed).
G21 G90 G17
M3 S10000
G0 X10 Y10 Z5
G1 Z-1 F600
G1 X30 Y10
G2 X50 Y30 I20 J0
G3 X70 Y10 R20
G91
G1 X5
G90
G20
G1 X2
G21
G0 X350 Y10
G0 X0 Y0 Z5
M5
