from PIL import Image

img = Image.open('app/src/main/res/drawable/home_reactor_bg.png')
w, h = img.size
print(f"Image size: {w}x{h}")
