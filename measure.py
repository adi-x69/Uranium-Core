from PIL import Image

img = Image.open('app/src/main/res/drawable/dna_animation.gif')
w, h = img.size
print(f"GIF size: {w}x{h}")

bg = Image.open('app/src/main/res/drawable/room_reactor_bg.png')
w2, h2 = bg.size
print(f"BG size: {w2}x{h2}")
