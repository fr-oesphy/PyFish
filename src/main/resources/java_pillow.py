import java.awt.image.BufferedImage
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.imageio.ImageIO
import java.io.File
import java.lang.System

NEAREST = 0
BOX = 4
BILINEAR = 2
HAMMING = 5
BICUBIC = 3
LANCZOS = 1

_MODE_MAP = {
    "RGB": java.awt.image.BufferedImage.TYPE_INT_RGB,
    "RGBA": java.awt.image.BufferedImage.TYPE_INT_ARGB,
    "L": java.awt.image.BufferedImage.TYPE_BYTE_GRAY
}

class UnidentifiedImageError(Exception):
    pass

class Image:
    def __init__(self, buffered_image):
        self._img = buffered_image
        self.readonly = False

    @property
    def width(self):
        return self._img.getWidth()

    @property
    def height(self):
        return self._img.getHeight()

    @property
    def size(self):
        return (self.width, self.height)

    @property
    def mode(self):
        t = self._img.getType()
        if t == java.awt.image.BufferedImage.TYPE_INT_ARGB:
            return "RGBA"
        elif t == java.awt.image.BufferedImage.TYPE_INT_RGB:
            return "RGB"
        elif t == java.awt.image.BufferedImage.TYPE_BYTE_GRAY:
            return "L"
        return "RGBA" if self._img.getColorModel().hasAlpha() else "RGB"

    def show(self, title=None):
        java.lang.System.out.println(f"[PyFish] Image.show() called. Size: {self.size}, Mode: {self.mode}")

    def save(self, fp, format=None):
        """
        Saves the image under the given filename. 
        If no format is specified, the format to use is determined from the filename extension, if possible.
        """
        if format is None:
            if isinstance(fp, str):
                if fp.lower().endswith(".png"):
                    format = "png"
                elif fp.lower().endswith(".jpg") or fp.lower().endswith(".jpeg"):
                    format = "jpg"
                elif fp.lower().endswith(".gif"):
                    format = "gif"
                else:
                    format = "png" # Default fallback
            else:
                raise ValueError("Format must be specified when saving to a file object")
        
        format = format.lower()
        
        # ImageIO cannot encode alpha in JPEG. Composite onto white explicitly instead of
        # relying on a writer-specific conversion, which often produces black backgrounds.
        img_to_save = self._img
        if format in ["jpg", "jpeg"] and self.mode == "RGBA":
            new_img = java.awt.image.BufferedImage(self.width, self.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            g = new_img.createGraphics()
            g.setColor(java.awt.Color.WHITE)
            g.fillRect(0, 0, self.width, self.height)
            g.drawImage(self._img, 0, 0, None)
            g.dispose()
            img_to_save = new_img

        if isinstance(fp, str):
            file = java.io.File(fp)
            javax.imageio.ImageIO.write(img_to_save, format, file)
        else:
            print(f"[PyFish] Warning: Image.save(fp) currently only supports file paths as strings.")

    def resize(self, size, resample=None):
        """
        Returns a resized copy of this image.
        """
        width, height = size
        image_type = self._img.getType()
        if image_type == 0: image_type = java.awt.image.BufferedImage.TYPE_INT_ARGB
        
        resized = java.awt.image.BufferedImage(width, height, image_type)
        g = resized.createGraphics()
        
        if resample == NEAREST:
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        elif resample == BILINEAR or resample == BICUBIC:
             g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        else:
             g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        g.drawImage(self._img, 0, 0, width, height, None)
        g.dispose()
        return Image(resized)

    def crop(self, box):
        """
        Returns a rectangular region from this image.
        The box is a 4-tuple defining the left, upper, right, and lower pixel coordinate.
        """
        left, upper, right, lower = box
        w = right - left
        h = lower - upper
        if w <= 0 or h <= 0:
            raise ValueError("Crop box has invalid dimensions")
        
        sub_img = self._img.getSubimage(left, upper, w, h)
        
        color_model = sub_img.getColorModel()
        is_alpha_premultiplied = color_model.isAlphaPremultiplied()
        writable_raster = sub_img.copyData(None)
        
        new_img = java.awt.image.BufferedImage(color_model, writable_raster, is_alpha_premultiplied, None)
        return Image(new_img)

    def copy(self):
        color_model = self._img.getColorModel()
        is_alpha_premultiplied = color_model.isAlphaPremultiplied()
        writable_raster = self._img.copyData(None)
        new_img = java.awt.image.BufferedImage(color_model, writable_raster, is_alpha_premultiplied, None)
        return Image(new_img)
        
    def paste(self, im, box=None, mask=None):
        """
        Pastes another image into this image.
        """
        if box is None:
            box = (0, 0)
        
        if len(box) == 2:
            x, y = box
        elif len(box) == 4:
            x, y, _, _ = box # Ignore bottom/right for paste dest
        else:
            raise ValueError("Box must be length 2 or 4")
            
        g = self._img.createGraphics()
        
        if hasattr(im, "_img"):
            g.drawImage(im._img, x, y, None)
        else:
             pass
             
        g.dispose()

    def getpixel(self, xy):
        x, y = xy
        # getRGB returns packed ARGB regardless of the BufferedImage's storage type.
        rgb = self._img.getRGB(x, y)
        c = java.awt.Color(rgb, True)
        
        mode = self.mode
        if mode == "RGBA":
            return (c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha())
        elif mode == "RGB":
            return (c.getRed(), c.getGreen(), c.getBlue())
        elif mode == "L":
            return c.getRed() 
        return (c.getRed(), c.getGreen(), c.getBlue())

    def putpixel(self, xy, value):
        x, y = xy
        
        r, g, b, a = 0, 0, 0, 255
        
        if isinstance(value, tuple):
            if len(value) == 3:
                r, g, b = value
            elif len(value) == 4:
                r, g, b, a = value
        elif isinstance(value, int):
            r = g = b = value
            
        color = java.awt.Color(r, g, b, a)
        self._img.setRGB(x, y, color.getRGB())


def open(fp, mode="r"):
    if isinstance(fp, str):
        f = java.io.File(fp)
        if not f.exists():
             raise FileNotFoundError(f"No such file: {fp}")
        try:
            img = javax.imageio.ImageIO.read(f)
            if img is None:
                raise UnidentifiedImageError(f"cannot identify image file {fp}")
            return Image(img)
        except Exception as e:
            raise UnidentifiedImageError(e)
    else:
        raise ValueError("open() currently only supports file paths as strings")

def new(mode, size, color=0):
    w, h = size
    
    j_type = _MODE_MAP.get(mode, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    
    img = java.awt.image.BufferedImage(w, h, j_type)
    
    r, g, b, a = 0, 0, 0, 255
    
    if isinstance(color, tuple):
        if len(color) == 3:
            r, g, b = color
            a = 255
        elif len(color) == 4:
             r, g, b, a = color
    elif isinstance(color, int):
        r = g = b = color
        if mode == "RGBA" or mode == "ARGB":
            a = color
        else:
            a = 255

    # java.awt.Color rejects out-of-range components, while Pillow commonly receives values
    # produced by arithmetic and clips them at the image boundary.
    r = max(0, min(255, r))
    g = max(0, min(255, g))
    b = max(0, min(255, b))
    a = max(0, min(255, a))

    c = java.awt.Color(r, g, b, a)
    
    graphics = img.createGraphics()
    # Src is required here: SrcOver would blend semi-transparent fill colors with the image's
    # initial transparent black pixels and change their RGB channels.
    graphics.setComposite(java.awt.AlphaComposite.Src)
    graphics.setColor(c)
    graphics.fillRect(0, 0, w, h)
    graphics.dispose()
    
    return Image(img)
