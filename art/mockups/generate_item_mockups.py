"""Generates 16x16 item texture mockups from the shared palette.

These are placeholders that keep datagen and code unblocked -- they are meant to be
redrawn by hand in Aseprite. Run from the repo root:

    python art/mockups/generate_item_mockups.py

Light comes from the upper left: highlights on top and left edges, shadow on the
bottom and right, no anti-aliasing, no colours outside art/palette.gpl.
"""

from pathlib import Path

from PIL import Image

OUT_DIR = Path("src/main/resources/assets/actualgenerators/textures/item")
BLOCK_DIR = Path("src/main/resources/assets/actualgenerators/textures/block")

# Ramps mirror art/palette.gpl, darkest first.
CASING = [(26, 29, 36), (45, 51, 62), (66, 74, 88), (92, 102, 118), (124, 136, 153)]
NICKEL = [(74, 70, 62), (110, 105, 93), (148, 143, 128), (186, 181, 166), (219, 216, 204)]
SILVER = [(84, 92, 104), (122, 132, 145), (160, 171, 183), (199, 208, 218), (232, 238, 244)]
COPPER = [(92, 45, 28), (138, 71, 41), (184, 105, 61), (216, 143, 92), (235, 179, 130)]
VERDIGRIS = [(32, 74, 62), (46, 105, 86), (66, 139, 112), (94, 172, 141), (130, 200, 170)]
FLUX = [(10, 48, 70), (16, 88, 128), (26, 138, 190), (62, 194, 236), (146, 236, 255)]
HEAT = [(74, 20, 14), (128, 40, 20), (186, 74, 28), (228, 122, 44), (250, 180, 84)]
VOID = [(40, 20, 60), (64, 34, 92), (96, 56, 132), (132, 88, 172), (176, 138, 212)]
GOLD = [(92, 64, 10), (140, 102, 20), (196, 152, 36), (232, 196, 76), (250, 232, 150)]
TRIM = [(38, 36, 38), (64, 61, 62), (92, 88, 89), (122, 118, 118), (156, 152, 151)]

SIZE = 16
TRANSPARENT = (0, 0, 0, 0)


class Canvas:
    def __init__(self, size=SIZE):
        self.size = size
        self.img = Image.new("RGBA", (size, size), TRANSPARENT)
        self.px = self.img.load()

    def dot(self, x, y, colour):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[x, y] = colour if len(colour) == 4 else colour + (255,)

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.dot(x, y, colour)

    def hline(self, x0, x1, y, colour):
        self.rect(x0, y, x1, y, colour)

    def vline(self, x, y0, y1, colour):
        self.rect(x, y0, x, y1, colour)

    def paint(self, art, ramp, ox=0, oy=0):
        """Draws a pixel map: each digit indexes the ramp darkest-first, '.' leaves it clear.

        Shapes small enough to see all at once are easier to judge written out than derived
        from rules -- a heap of powder has no formula, it just has to look like one.
        """
        for y, row in enumerate(art):
            for x, char in enumerate(row):
                if char != ".":
                    self.dot(ox + x, oy + y, ramp[int(char)])

    def save(self, name, directory=OUT_DIR):
        directory.mkdir(parents=True, exist_ok=True)
        self.img.save(directory / f"{name}.png")
        print(f"wrote {directory / name}.png")


def upgrade_chip(name, accent, stacked=False):
    """A circuit chip with contact pins; the accent ramp is what tells the upgrades apart.

    `stacked` swaps the solid core for layered bars, so the stack upgrade reads as
    "more per operation" rather than just another colour.
    """
    c = Canvas()

    # Contact pins down both sides.
    for y in (4, 7, 10):
        c.dot(0, y, SILVER[1])
        c.dot(1, y, SILVER[3])
        c.dot(14, y, SILVER[3])
        c.dot(15, y, SILVER[1])

    # Chip body with a dark outline.
    c.rect(2, 2, 13, 12, CASING[0])
    c.rect(3, 3, 12, 11, CASING[2])

    # Bevel: lit from the upper left.
    c.hline(3, 12, 3, CASING[4])
    c.vline(3, 3, 11, CASING[3])
    c.hline(3, 12, 11, CASING[1])
    c.vline(12, 4, 11, CASING[1])

    # The accent core.
    c.rect(5, 5, 10, 9, accent[0])
    if stacked:
        # Three layers: one operation carrying several items.
        c.hline(6, 9, 5, accent[3])
        c.hline(6, 9, 6, accent[1])
        c.hline(6, 9, 7, accent[3])
        c.hline(6, 9, 8, accent[1])
        c.hline(6, 9, 9, accent[4])
    else:
        c.rect(6, 6, 9, 8, accent[2])
        c.hline(6, 9, 6, accent[3])
        c.rect(7, 7, 8, 7, accent[4])
        c.hline(6, 9, 8, accent[1])

    # Traces running from the core out to the pins.
    c.dot(4, 4, accent[1])
    c.dot(11, 10, accent[1])

    c.save(name)


def config_card():
    """A flat card with a data stripe -- deliberately not chip-shaped, so it reads as a tool."""
    c = Canvas()

    # Card body, corners clipped so it reads as rounded at this size.
    c.rect(3, 2, 12, 13, NICKEL[0])
    c.rect(4, 3, 11, 12, NICKEL[2])
    for x, y in ((3, 2), (12, 2), (3, 13), (12, 13)):
        c.dot(x, y, TRANSPARENT)

    # Bevel.
    c.hline(4, 11, 3, NICKEL[4])
    c.vline(4, 3, 12, NICKEL[3])
    c.hline(4, 11, 12, NICKEL[1])
    c.vline(11, 4, 12, NICKEL[1])

    # Magnetic stripe.
    c.rect(4, 5, 11, 6, CASING[1])
    c.hline(4, 11, 5, CASING[2])

    # Stored-configuration readout: a little grid of contacts.
    for y in (8, 10):
        for x in (5, 7, 9):
            c.dot(x, y, FLUX[3])
            c.dot(x + 1, y, FLUX[1])

    c.save("config_card")


def machine_casing():
    """The shared machine shell every block face starts from: bevelled panel with rivets."""
    c = Canvas()
    c.rect(0, 0, 15, 15, CASING[1])
    c.rect(1, 1, 14, 14, CASING[2])
    c.hline(1, 14, 1, CASING[3])
    c.vline(1, 1, 14, CASING[3])
    c.hline(1, 14, 14, CASING[0])
    c.vline(14, 1, 14, CASING[0])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.dot(x, y, CASING[4])
    return c


def corrosion_cell_side():
    c = machine_casing()
    # Cooling vents.
    for y in (5, 8, 11):
        c.rect(4, y, 11, y, CASING[0])
        c.hline(4, 11, y - 1, CASING[3])
    c.save("corrosion_cell_side", BLOCK_DIR)


def corrosion_cell_front():
    c = machine_casing()
    # The reaction window: copper on the left oxidising to verdigris on the right.
    c.rect(3, 4, 12, 11, CASING[0])
    c.rect(4, 5, 11, 10, COPPER[1])
    for x in range(4, 12):
        ramp = COPPER if x < 6 else (COPPER if x < 8 else VERDIGRIS)
        shade = 3 if x < 6 else (2 if x < 8 else 2)
        c.vline(x, 5, 10, ramp[shade])
    for x in range(10, 12):
        c.vline(x, 5, 10, VERDIGRIS[3])
    # Glass edge highlights.
    c.hline(4, 11, 5, NICKEL[4])
    c.hline(4, 11, 10, CASING[0])
    c.save("corrosion_cell_front", BLOCK_DIR)


def corrosion_cell_top():
    c = machine_casing()
    # Input hopper grate.
    c.rect(4, 4, 11, 11, CASING[0])
    for y in range(5, 11, 2):
        for x in range(5, 11, 2):
            c.dot(x, y, CASING[3])
            c.dot(x + 1, y, CASING[1])
    c.save("corrosion_cell_top", BLOCK_DIR)


def corrosion_cell_bottom():
    c = machine_casing()
    # Output chute.
    c.rect(5, 5, 10, 10, CASING[0])
    c.rect(6, 6, 9, 9, CASING[1])
    c.hline(6, 9, 6, CASING[2])
    c.save("corrosion_cell_bottom", BLOCK_DIR)


def hydrostatic_generator_side():
    c = machine_casing()
    # Sight glass: the water level you are being paid for, running up the side.
    c.rect(5, 3, 10, 12, CASING[0])
    c.rect(6, 4, 9, 11, FLUX[1])
    c.rect(6, 4, 9, 5, FLUX[2])
    c.hline(6, 9, 4, FLUX[3])
    # Level marks etched either side of the glass.
    for y in (5, 8, 11):
        c.dot(4, y, NICKEL[3])
        c.dot(11, y, NICKEL[1])
    c.save("hydrostatic_generator_side", BLOCK_DIR)


def hydrostatic_generator_front():
    c = machine_casing()
    # Pressure dial, needle swung hard over to the right.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, NICKEL[2])
    c.hline(5, 10, 5, NICKEL[4])
    c.hline(5, 10, 10, NICKEL[1])
    # Face ticks.
    c.dot(6, 6, CASING[1])
    c.dot(9, 6, CASING[1])
    c.dot(6, 9, CASING[1])
    c.dot(9, 9, CASING[1])
    # Needle and hub.
    c.dot(7, 8, FLUX[0])
    c.dot(8, 8, FLUX[0])
    c.dot(9, 7, FLUX[1])
    c.rect(7, 7, 8, 7, CASING[0])
    c.save("hydrostatic_generator_front", BLOCK_DIR)


def hydrostatic_generator_top():
    c = machine_casing()
    # Intake: open to the column standing on it.
    c.rect(3, 3, 12, 12, CASING[0])
    c.rect(4, 4, 11, 11, FLUX[1])
    c.rect(5, 5, 10, 10, FLUX[2])
    c.hline(5, 10, 5, FLUX[3])
    # Grate bars across the intake.
    for x in (6, 9):
        c.vline(x, 4, 11, CASING[1])
    c.save("hydrostatic_generator_top", BLOCK_DIR)


def hydrostatic_generator_bottom():
    c = machine_casing()
    # Turbine housing.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, CASING[1])
    c.rect(6, 6, 9, 9, NICKEL[1])
    c.dot(7, 7, NICKEL[3])
    c.dot(8, 8, NICKEL[3])
    c.dot(8, 7, CASING[0])
    c.dot(7, 8, CASING[0])
    c.save("hydrostatic_generator_bottom", BLOCK_DIR)


def photovore_side():
    c = machine_casing()
    # Ribbed vine-like housing: it is closer to a plant than to a machine.
    for y in range(3, 13, 3):
        c.hline(3, 12, y, VERDIGRIS[1])
        c.hline(3, 12, y + 1, VERDIGRIS[2])
    c.vline(7, 3, 13, VERDIGRIS[3])
    c.vline(8, 3, 13, VERDIGRIS[0])
    c.save("photovore_side", BLOCK_DIR)


def photovore_front():
    c = machine_casing()
    # The eye it grazes with, half open and lit from inside.
    c.rect(3, 4, 12, 11, CASING[0])
    c.rect(4, 5, 11, 10, VERDIGRIS[1])
    # Iris.
    c.rect(6, 6, 9, 9, HEAT[1])
    c.rect(7, 7, 8, 8, HEAT[3])
    c.dot(7, 7, HEAT[4])
    # Lids.
    c.hline(4, 11, 5, VERDIGRIS[3])
    c.hline(4, 11, 10, VERDIGRIS[0])
    c.save("photovore_front", BLOCK_DIR)


def photovore_top():
    c = machine_casing()
    # Petals folded back around a bright centre.
    c.rect(4, 4, 11, 11, VERDIGRIS[1])
    c.rect(5, 5, 10, 10, VERDIGRIS[2])
    c.rect(6, 6, 9, 9, HEAT[2])
    c.rect(7, 7, 8, 8, HEAT[4])
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        c.dot(x, y, VERDIGRIS[3])
    c.save("photovore_top", BLOCK_DIR)


def photovore_bottom():
    c = machine_casing()
    # Root plate.
    c.rect(4, 4, 11, 11, CASING[0])
    for y in range(5, 11, 2):
        c.hline(5, 10, y, VERDIGRIS[0])
        c.hline(5, 10, y + 1, VERDIGRIS[1])
    c.save("photovore_bottom", BLOCK_DIR)


def impact_dynamo_side():
    c = machine_casing()
    # Shock absorbers: stacked plates squeezed between two heavy rails.
    c.vline(3, 3, 12, NICKEL[1])
    c.vline(12, 3, 12, NICKEL[1])
    for y in (4, 6, 8, 10):
        c.rect(4, y, 11, y, NICKEL[3])
        c.rect(4, y + 1, 11, y + 1, CASING[0])
    c.save("impact_dynamo_side", BLOCK_DIR)


def impact_dynamo_front():
    c = machine_casing()
    # Pressure readout, needle buried at the top of the scale after a hit.
    c.rect(4, 5, 11, 10, CASING[0])
    c.rect(5, 6, 10, 9, NICKEL[1])
    for x in range(5, 11):
        shade = 1 if x < 7 else (3 if x < 9 else 4)
        c.vline(x, 6, 6, HEAT[shade])
    c.hline(5, 10, 9, CASING[1])
    # Impact marks hammered into the plate below.
    c.dot(6, 12, CASING[0])
    c.dot(9, 12, CASING[0])
    c.dot(7, 13, CASING[0])
    c.save("impact_dynamo_front", BLOCK_DIR)


def impact_dynamo_top():
    c = machine_casing()
    # The anvil face: a scarred steel plate that things land on.
    c.rect(2, 2, 13, 13, NICKEL[1])
    c.rect(3, 3, 12, 12, NICKEL[2])
    c.hline(3, 12, 3, NICKEL[4])
    c.vline(3, 3, 12, NICKEL[3])
    c.hline(3, 12, 12, NICKEL[0])
    c.vline(12, 4, 12, NICKEL[0])
    # Dents, off centre so the plate does not read as a pattern.
    for x, y in ((5, 6), (6, 7), (9, 5), (10, 9), (7, 10)):
        c.dot(x, y, NICKEL[0])
        c.dot(x + 1, y, NICKEL[3])
    c.save("impact_dynamo_top", BLOCK_DIR)


def impact_dynamo_bottom():
    c = machine_casing()
    # Salvage chute, wide enough to read as an opening.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, CASING[1])
    c.rect(6, 6, 9, 9, CASING[0])
    c.hline(5, 10, 5, CASING[2])
    c.save("impact_dynamo_bottom", BLOCK_DIR)


def spawner_siphon_side():
    c = machine_casing()
    # Cage bars with something dark behind them.
    c.rect(3, 3, 12, 12, VOID[0])
    for x in (4, 7, 10):
        c.vline(x, 3, 12, CASING[3])
        c.vline(x + 1, 3, 12, CASING[1])
    c.hline(3, 12, 3, CASING[4])
    c.hline(3, 12, 12, CASING[0])
    c.save("spawner_siphon_side", BLOCK_DIR)


def spawner_siphon_front():
    c = machine_casing()
    # The tap itself: a funnel drawing a spawn down into the machine.
    c.rect(3, 3, 12, 12, CASING[0])
    for i, y in enumerate(range(4, 9)):
        inset = i
        c.rect(4 + inset, y, 11 - inset, y, VOID[4 - min(i, 4)])
    c.rect(7, 9, 8, 11, VOID[1])
    c.dot(7, 9, VOID[3])
    # Cage corners kept, so it still reads as a spawner part.
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.dot(x, y, CASING[4])
    c.save("spawner_siphon_front", BLOCK_DIR)


def spawner_siphon_top():
    c = machine_casing()
    # Containment ring around a dark core.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, VOID[1])
    c.rect(6, 6, 9, 9, VOID[0])
    c.dot(7, 7, VOID[3])
    c.dot(8, 8, VOID[2])
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.dot(x, y, VOID[4])
    c.save("spawner_siphon_top", BLOCK_DIR)


def spawner_siphon_bottom():
    c = machine_casing()
    # Anchor plate with the same dark seam running through it.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, CASING[1])
    c.hline(5, 10, 7, VOID[2])
    c.hline(5, 10, 8, VOID[1])
    c.save("spawner_siphon_bottom", BLOCK_DIR)


def enchantment_combustor_side():
    c = machine_casing()
    # Glyph columns burning down the flanks.
    for i, y in enumerate(range(3, 13, 3)):
        c.hline(4, 11, y, VOID[2])
        for x in range(4, 12, 2):
            c.dot(x, y, FLUX[3 - (i % 2)])
    c.vline(3, 3, 12, VOID[0])
    c.vline(12, 3, 12, VOID[0])
    c.save("enchantment_combustor_side", BLOCK_DIR)


def enchantment_combustor_front():
    c = machine_casing()
    # A book held open in a furnace mouth, pages coming apart into sparks.
    c.rect(3, 4, 12, 11, CASING[0])
    c.rect(4, 5, 11, 10, VOID[0])
    # Two pages with a spine down the middle.
    c.rect(4, 6, 7, 9, NICKEL[3])
    c.rect(8, 6, 11, 9, NICKEL[2])
    c.vline(7, 5, 10, VOID[2])
    c.vline(8, 5, 10, VOID[1])
    # The lettering lifting off the pages.
    for y in (6, 8):
        c.dot(5, y, FLUX[3])
        c.dot(10, y, FLUX[2])
    c.dot(6, 5, FLUX[4])
    c.dot(9, 5, FLUX[3])
    c.save("enchantment_combustor_front", BLOCK_DIR)


def enchantment_combustor_top():
    c = machine_casing()
    # Feed slot, lit from below by whatever is burning.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, VOID[1])
    c.rect(6, 7, 9, 8, VOID[3])
    c.hline(6, 9, 7, FLUX[3])
    for x, y in ((5, 5), (10, 10)):
        c.dot(x, y, VOID[4])
    c.save("enchantment_combustor_top", BLOCK_DIR)


def enchantment_combustor_bottom():
    c = machine_casing()
    # Ash chute: what is left of an enchantment falls out here.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, CASING[1])
    c.rect(6, 6, 9, 9, VOID[0])
    c.dot(7, 8, VOID[2])
    c.dot(8, 7, VOID[2])
    c.save("enchantment_combustor_bottom", BLOCK_DIR)


GUI_DIR = Path("src/main/resources/assets/actualgenerators/textures/gui")

# Vanilla container chrome, so the machine GUI sits next to a chest without clashing.
GUI_BG = (198, 198, 198)
GUI_LIGHT = (255, 255, 255)
GUI_DARK = (85, 85, 85)
SLOT_BG = (139, 139, 139)
SLOT_DARK = (55, 55, 55)


class Sheet:
    """A 256x256 GUI sheet: the window itself plus the sprites drawn on top of it."""

    def __init__(self, size=256):
        self.img = Image.new("RGBA", (size, size), TRANSPARENT)
        self.px = self.img.load()
        self.size = size

    def dot(self, x, y, colour):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[x, y] = colour if len(colour) == 4 else colour + (255,)

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.dot(x, y, colour)

    def paint(self, art, ramp, ox=0, oy=0):
        """A pixel map, as Canvas.paint: digits index the ramp, '.' leaves the sheet alone."""
        for y, row in enumerate(art):
            for x, char in enumerate(row):
                if char != ".":
                    self.dot(ox + x, oy + y, ramp[int(char)])

    def panel(self, x0, y0, x1, y1):
        """Raised panel: lit from the upper left, like every vanilla container."""
        self.rect(x0, y0, x1, y1, GUI_BG)
        self.rect(x0, y0, x1, y0, GUI_LIGHT)
        self.rect(x0, y0, x0, y1, GUI_LIGHT)
        self.rect(x0, y1, x1, y1, GUI_DARK)
        self.rect(x1, y0, x1, y1, GUI_DARK)

    def slot(self, x, y):
        """An 18x18 sunken slot at its top-left corner."""
        self.rect(x, y, x + 17, y + 17, SLOT_BG)
        self.rect(x, y, x + 17, y, SLOT_DARK)
        self.rect(x, y, x, y + 17, SLOT_DARK)
        self.rect(x, y + 17, x + 17, y + 17, GUI_LIGHT)
        self.rect(x + 17, y, x + 17, y + 17, GUI_LIGHT)

    def sunken(self, x0, y0, x1, y1, fill):
        self.rect(x0, y0, x1, y1, fill)
        self.rect(x0, y0, x1, y0, SLOT_DARK)
        self.rect(x0, y0, x0, y1, SLOT_DARK)
        self.rect(x0, y1, x1, y1, GUI_LIGHT)
        self.rect(x1, y0, x1, y1, GUI_LIGHT)

    def save(self, name):
        GUI_DIR.mkdir(parents=True, exist_ok=True)
        self.img.save(GUI_DIR / f"{name}.png")
        print(f"wrote {GUI_DIR / name}.png")


GUI_WIDTH, GUI_HEIGHT = 176, 194


def machine_casing_block(formed=False):
    """The wall block of every multiblock. Loose, the casing with a cross brace; formed, the
    bevelled plate whose rim the connected model shows where a structure ends (its middle is
    drawn from the panel tiles, so it stays plain)."""
    c = machine_casing()
    if formed:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            c.dot(x, y, HEAT[3])
    else:
        c.hline(3, 12, 7, CASING[1])
        c.hline(3, 12, 8, CASING[3])
        c.vline(7, 3, 12, CASING[1])
        c.vline(8, 3, 12, CASING[3])
        c.rect(7, 7, 8, 8, CASING[0])
    c.save("machine_casing_formed" if formed else "machine_casing", BLOCK_DIR)


def geothermal_casing_block():
    """The plate block under a tap, loose: the casing panel with copper fins across it."""
    c = machine_casing()
    for y in (4, 7, 10):
        c.hline(3, 12, y, COPPER[3])
        c.hline(3, 12, y + 1, COPPER[1])
    c.vline(7, 3, 12, COPPER[2])
    c.vline(8, 3, 12, COPPER[1])
    c.save("geothermal_casing", BLOCK_DIR)


def machine_casing_panel():
    """The plain plate of a formed wall: what a casing shows between the rims, and most of the
    wall's middles. Flat on purpose; the detail tiles carry the interest."""
    c = Canvas()
    c.rect(0, 0, 15, 15, CASING[2])
    c.save("machine_casing_panel", BLOCK_DIR)


def machine_casing_panel_detail(index):
    """One of the detail tiles a formed wall's middles are picked from by position: bolts, a
    vent, a recessed panel, a pilot light. Each sits inside the rim, so it never meets a seam."""
    c = Canvas()
    c.rect(0, 0, 15, 15, CASING[2])
    if index == 1:
        for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
            c.dot(x, y, CASING[4])
            c.dot(x + 1, y + 1, CASING[1])
    elif index == 2:
        for y in (5, 8, 11):
            c.hline(3, 12, y, CASING[0])
            c.hline(3, 12, y + 1, CASING[1])
    elif index == 3:
        c.rect(3, 3, 12, 12, CASING[1])
        c.rect(4, 4, 11, 11, CASING[2])
        c.hline(4, 11, 4, CASING[3])
        c.vline(4, 4, 11, CASING[3])
    else:
        c.rect(6, 6, 9, 9, CASING[0])
        c.rect(7, 7, 8, 8, HEAT[3])
    c.save("machine_casing_panel_%d" % index, BLOCK_DIR)


# The fins of the plate's rings: three light fins between dark grooves, edge to edge. The rows
# read the same from either side (row k is row 15 - k), so a piece laid any way round meets its
# neighbour and every corner of a ring lines up.
FIN_ROWS = {3: HEAT[1], 4: HEAT[3], 5: HEAT[3], 6: HEAT[1], 7: HEAT[3], 8: HEAT[3], 9: HEAT[1],
            10: HEAT[3], 11: HEAT[3], 12: HEAT[1]}


def geothermal_casing_plate():
    """The plain plate between the rings, and under the cap: flat, four rivets."""
    c = Canvas()
    c.rect(0, 0, 15, 15, CASING[2])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.dot(x, y, CASING[3])
    c.save("geothermal_casing_plate", BLOCK_DIR)


def geothermal_casing_ring():
    """A straight piece of a ring: three hot fins running left to right, edge to edge, so they
    run on into the next block and round the plate's side as well."""
    c = Canvas()
    c.rect(0, 0, 15, 15, CASING[2])
    for y, colour in FIN_ROWS.items():
        c.hline(0, 15, y, colour)
    c.save("geothermal_casing_ring", BLOCK_DIR)


def geothermal_casing_ring_corner():
    """A corner of a ring: the fins bend round the top-left corner, the one that faces the centre."""
    c = Canvas()
    c.rect(0, 0, 15, 15, CASING[2])
    for y in range(16):
        for x in range(16):
            k = max(x, y)
            if k in FIN_ROWS:
                c.dot(x, y, FIN_ROWS[k])
    c.save("geothermal_casing_ring_corner", BLOCK_DIR)


# Thermal's way: the colour of the panel's face buttons on the face itself.
MARKER_INPUT = (62, 155, 216)
MARKER_OUTPUT = (216, 129, 62)


def hatch_marker(mode):
    """A one-pixel frame a pixel in from the edge, in the mode's colour; "both" is split along
    the diagonal, input in the upper right and output in the lower left, like the panel."""
    c = Canvas()
    for y in range(1, 15):
        for x in range(1, 15):
            if x in (1, 14) or y in (1, 14):
                if mode == "input":
                    colour = MARKER_INPUT
                elif mode == "output":
                    colour = MARKER_OUTPUT
                else:
                    colour = MARKER_INPUT if x >= y else MARKER_OUTPUT
                c.dot(x, y, colour)
    c.save("hatch_marker_" + mode, BLOCK_DIR)


def hatch(name, art, ramp, formed=False):
    """A casing with an opening and one glyph in it: what the hatch lets through. Formed, the rim burns."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, HEAT[1] if formed else CASING[0])
    if formed:
        c.rect(4, 4, 11, 11, CASING[0])
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            c.dot(x, y, HEAT[3])
    c.paint(art, ramp, 4, 4)
    c.save(name + "_formed" if formed else name, BLOCK_DIR)


ITEM_HATCH_ART = [
    "........",
    "33333333",
    "32222223",
    "32222223",
    "31111113",
    "31111113",
    "00000000",
    "........",
]
ENERGY_HATCH_ART = [
    "....33..",
    "...34...",
    "..344...",
    ".3444333",
    "3334443.",
    "...443..",
    "...43...",
    "..33....",
]
REDSTONE_HATCH_ART = [
    "........",
    "..1111..",
    ".111111.",
    ".112211.",
    ".112211.",
    ".111111.",
    "..1111..",
    "....0...",
]


def item_hatch(formed=False):
    # A tray with a lip: things go in here.
    hatch("item_hatch", ITEM_HATCH_ART, NICKEL, formed)


def energy_hatch(formed=False):
    # A bolt.
    hatch("energy_hatch", ENERGY_HATCH_ART, FLUX, formed)


def redstone_hatch(formed=False):
    # A torch head seen straight on: dark red round a lit centre.
    hatch("redstone_hatch", REDSTONE_HATCH_ART, HEAT, formed)


FLUID_HATCH_ART = [
    "...33...",
    "...33...",
    "..3333..",
    ".344333.",
    ".344333.",
    ".333333.",
    "..3333..",
    "...11...",
]


def fluid_hatch(formed=False):
    # A drop, lit on its upper left.
    hatch("fluid_hatch", FLUID_HATCH_ART, VERDIGRIS, formed)


# Corium: a crust of cooled rock over a glow that shows through the cracks.
CORIUM = [(30, 14, 10), (58, 26, 16), (128, 40, 20), (228, 122, 44), (250, 200, 96)]
CORIUM_ART = [
    "1100111011100011",
    "1011122111011101",
    "0112331121101110",
    "1123321101110011",
    "1122111011332111",
    "0111100112443211",
    "1101101112332110",
    "1100111011221011",
    "0111011101110111",
    "1122111101011122",
    "1233211011101233",
    "1123321110111122",
    "0112211123211011",
    "1101101233321101",
    "1110011122211011",
    "0110110111101110",
]


def corium_still():
    c = Canvas()
    c.paint(CORIUM_ART, CORIUM)
    c.save("corium_still", BLOCK_DIR)


def corium_flow():
    """The flowing sprite is 32 wide like lava's: the still crust, tiled, the other copies turned so the seam hides."""
    c = Canvas(32)
    c.paint(CORIUM_ART, CORIUM, 0, 0)
    c.paint([row[::-1] for row in CORIUM_ART], CORIUM, 16, 0)
    c.paint(CORIUM_ART[::-1], CORIUM, 0, 16)
    c.paint([row[::-1] for row in CORIUM_ART[::-1]], CORIUM, 16, 16)
    c.save("corium_flow", BLOCK_DIR)


def geothermal_tap_side():
    c = machine_casing()
    # The bore pipe, running down into the floor, glowing where the heat comes up.
    c.rect(6, 2, 9, 13, CASING[0])
    c.rect(7, 2, 8, 13, COPPER[2])
    c.vline(7, 2, 13, COPPER[3])
    for y in (4, 8, 12):
        c.hline(6, 9, y, COPPER[1])
    c.rect(7, 11, 8, 13, HEAT[3])
    c.dot(7, 13, HEAT[4])
    c.save("geothermal_tap_side", BLOCK_DIR)


def geothermal_tap_front(formed=False):
    """The wellhead: a heat dial above a sight glass of corium. Formed, the rim burns like every shell block's."""
    c = machine_casing()
    c.rect(4, 3, 11, 8, CASING[0])
    c.rect(5, 4, 10, 7, NICKEL[2])
    c.hline(5, 10, 4, NICKEL[4])
    c.dot(6, 6, HEAT[2])
    c.dot(7, 5, HEAT[3])
    c.dot(8, 5, HEAT[3])
    c.dot(9, 6, HEAT[2])
    c.rect(7, 6, 8, 6, CASING[0])
    c.rect(4, 9, 11, 13, CASING[0])
    c.rect(5, 10, 10, 12, CORIUM[1])
    c.hline(5, 10, 12, CORIUM[3])
    c.dot(7, 11, CORIUM[3])
    c.dot(8, 10, CORIUM[2])
    if formed:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            c.dot(x, y, HEAT[3])
        c.hline(5, 10, 12, CORIUM[4])
        c.dot(6, 6, HEAT[3])
        c.dot(9, 6, HEAT[3])
        c.dot(7, 5, HEAT[4])
        c.dot(8, 5, HEAT[4])
    c.save("geothermal_tap_front_formed" if formed else "geothermal_tap_front", BLOCK_DIR)


def geothermal_tap_top():
    c = machine_casing()
    # The valve wheel on the wellhead.
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, COPPER[1])
    c.rect(6, 6, 9, 9, CASING[0])
    c.rect(7, 7, 8, 8, COPPER[3])
    c.vline(7, 4, 11, COPPER[2])
    c.hline(4, 11, 7, COPPER[2])
    c.vline(8, 4, 11, COPPER[3])
    c.hline(4, 11, 8, COPPER[3])
    c.save("geothermal_tap_top", BLOCK_DIR)


def geothermal_tap_bottom():
    c = machine_casing()
    # The bore mouth: the fissure it sits over, heat coming up through it.
    c.rect(3, 3, 12, 12, CASING[0])
    c.rect(4, 4, 11, 11, CORIUM[0])
    c.paint([
        "1..1....",
        ".12.1...",
        "..23.1..",
        "1.2432.1",
        ".1.343.1",
        "..1.2.1.",
        ".1..1...",
        "1...1..1",
    ], CORIUM, 4, 4)
    c.save("geothermal_tap_bottom", BLOCK_DIR)


def annihilation_furnace_front(formed):
    """The controller: a viewing port on to the void inside. Formed, the rim burns."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[0])
    c.paint([
        "00000000",
        "01111110",
        "01222210",
        "01233210",
        "01233210",
        "01222210",
        "01111110",
        "00000000",
    ], VOID, 4, 4)
    if formed:
        for x in range(4, 12):
            c.dot(x, 4, HEAT[3])
            c.dot(x, 11, HEAT[2])
        for y in range(5, 11):
            c.dot(4, y, HEAT[3])
            c.dot(11, y, HEAT[2])
        c.dot(7, 7, HEAT[4])
        c.dot(8, 8, HEAT[4])
    c.save("annihilation_furnace_front_formed" if formed else "annihilation_furnace_front", BLOCK_DIR)


def gui_chrome(s):
    """Everything every machine window has: the frame, the energy bar, the player inventory.

    Nothing machine-specific is baked in. How many slots a machine shows, whether it has a
    progress arrow, whether it has a gauge -- all of that varies per machine, so those pieces are
    sprites blitted at runtime and the window underneath stays the same for every one of them.
    """
    s.panel(0, 0, GUI_WIDTH - 1, GUI_HEIGHT - 1)

    # Energy bar.
    s.sunken(151, 17, 164, 71, SLOT_DARK)

    # Player inventory and hotbar. Pushed down so the "Inventory" label has a line of its own
    # instead of sitting on top of the upgrade row.
    for row in range(3):
        for column in range(9):
            s.slot(7 + column * 18, 111 + row * 18)
    for column in range(9):
        s.slot(7 + column * 18, 169)


def gui_sprites(s):
    """The overlays drawn on top of a window at runtime.

    These sit at identical coordinates on every sheet, so screen code can share one set of
    constants no matter which window a machine uses.
    """
    # Filled progress arrow (24x13) at (176, 0).
    for x in range(24):
        for y in range(13):
            edge = y in (0, 12) or x == 0
            s.dot(176 + x, y, COPPER[2] if not edge else COPPER[1])
    for x in range(4, 20):
        s.dot(176 + x, 5, COPPER[4])
        s.dot(176 + x, 6, COPPER[3])

    # Energy fill (12x53) at (176, 16), bright at the top so a full bar reads as full.
    for y in range(53):
        ramp = FLUX[4] if y < 6 else (FLUX[3] if y < 26 else FLUX[2])
        for x in range(12):
            s.dot(176 + x, 16 + y, ramp)
        s.dot(176, 16 + y, FLUX[1])
        s.dot(176 + 11, 16 + y, FLUX[1])

    # Overclock fill (46x16) at (176, 180) and its frame (48x18) at (176, 198). The ramp is the
    # one number that moves while you watch it, so it gets a bar you can actually read.
    for y in range(16):
        for x in range(46):
            shade = 4 if y < 2 else (3 if y < 8 else (2 if y < 14 else 1))
            s.dot(176 + x, 180 + y, VOID[shade])
    # A leading edge, so a part-filled bar has a visible head rather than a soft fade.
    for y in range(16):
        s.dot(176 + 45, 180 + y, VOID[4])
    s.sunken(176, 198, 223, 215, SLOT_DARK)

    # Water fill (16x53) at (176, 80). Darker than the energy bar and banded, so a column of
    # water never gets mistaken for a charge level.
    for y in range(53):
        base = FLUX[1] if y % 6 < 3 else FLUX[0]
        for x in range(16):
            s.dot(176 + x, 80 + y, base)
        s.dot(176, 80 + y, FLUX[0])
        s.dot(176 + 15, 80 + y, FLUX[0])
    # Surface highlight along the top of the fill.
    for x in range(1, 15):
        s.dot(176 + x, 80, FLUX[3])
        s.dot(176 + x, 81, FLUX[2])

    # Light fill (16x53) at (192, 80): the Photovore's meal, warm rather than electric so it
    # never reads as another energy bar.
    for y in range(53):
        base = HEAT[3] if y % 6 < 3 else HEAT[2]
        for x in range(16):
            s.dot(192 + x, 80 + y, base)
        s.dot(192, 80 + y, HEAT[1])
        s.dot(192 + 15, 80 + y, HEAT[1])
    for x in range(1, 15):
        s.dot(192 + x, 80, HEAT[4])
        s.dot(192 + x, 81, HEAT[3])

    # Empty-slot hints (16x16 each) at (176, 136), in UpgradeType order.
    for i, (accent, stacked) in enumerate(
            ((FLUX, False), (COPPER, False), (VOID, False), (VERDIGRIS, True))):
        ghost_icon(s, 176 + i * GHOST_SIZE, 136, accent, stacked)

    # Slot frame (18x18) at (176, 154), drawn behind every machine slot.
    s.slot(176, 154)

    # Filter entry gears (8x8) at (200, 154) and (208, 154): grey says "this entry has settings",
    # the lit one says "its data has to match". Drawn over the entry's bottom-right corner.
    s.paint(GEAR, GEAR_GREY, 200, 154)
    s.paint(GEAR, FLUX, 208, 154)

    # Empty tier-slot hint (16x16) at (240, 136), after the four upgrade hints.
    ghost_tier(s, 240, 136)

    # Progress arrow track (24x13) at (200, 0): the empty groove the arrow fills.
    s.sunken(200, 0, 223, 12, SLOT_BG)

    # Gauge frame (18x55) at (208, 16), for generators that read the world.
    s.sunken(208, 16, 225, 70, SLOT_DARK)


GHOST_SIZE = 16
# How much of the accent survives; the rest is the slot underneath, so the hint reads as
# engraved into the slot rather than as an item sitting in it.
GHOST_STRENGTH = 0.34

# A gear small enough to sit in the corner of a 16x16 entry: a ring with four teeth.
GEAR = [
    "..1..1..",
    ".111111.",
    "11133111",
    ".13..31.",
    ".13..31.",
    "11133111",
    ".111111.",
    "..1..1..",
]
GEAR_GREY = [(40, 40, 40), (70, 70, 70), (110, 110, 110), (150, 150, 150), (190, 190, 190)]

# A chevron over a bar: the block itself goes up a grade. Shared by the four tier items and the
# tier slot's hint, so the hint is recognisably the item that belongs there.
TIER_ART = [
    "................",
    "................",
    ".......00.......",
    "......0440......",
    ".....044340.....",
    "....04433340....",
    "...0443..3340...",
    "..0443....3340..",
    "..043......340..",
    "..00........00..",
    "................",
    "....00000000....",
    "....04444330....",
    "....03333220....",
    "....00000000....",
    "................",
]


def _faded(colour):
    return tuple(round(c * GHOST_STRENGTH + b * (1.0 - GHOST_STRENGTH)) for c, b in zip(colour, SLOT_BG))


def ghost_icon(s, x, y, accent, stacked):
    """A washed-out upgrade chip, drawn straight onto the slot colour so it needs no blending."""
    for dy in range(GHOST_SIZE):
        for dx in range(GHOST_SIZE):
            s.dot(x + dx, y + dy, SLOT_BG)

    def put(x0, y0, x1, y1, colour):
        faded = _faded(colour)
        for py in range(y0, y1 + 1):
            for px in range(x0, x1 + 1):
                s.dot(x + px, y + py, faded)

    # Chip body, one pixel in from the chip item so the hint sits inside the slot.
    put(2, 2, 13, 12, CASING[0])
    put(3, 3, 12, 11, CASING[2])

    # The accent core is what names the upgrade.
    put(5, 5, 10, 9, accent[0])
    if stacked:
        for row, shade in ((5, 3), (6, 1), (7, 3), (8, 1), (9, 4)):
            put(6, row, 9, row, accent[shade])
    else:
        put(6, 6, 9, 8, accent[2])
        put(7, 7, 8, 7, accent[4])


def ghost_tier(s, x, y):
    """A washed-out tier chevron, the hint for an empty tier slot."""
    for dy in range(GHOST_SIZE):
        for dx in range(GHOST_SIZE):
            s.dot(x + dx, y + dy, SLOT_BG)
    s.paint(TIER_ART, [_faded(colour) for colour in TRIM], x, y)


def tier_upgrade(name, ramp):
    """A chevron over a bar in the material's colours: one per block, it upgrades the block itself."""
    c = Canvas()
    c.paint(TIER_ART, ramp)
    c.save(name)


def surge_bank_side():
    """Stacked cells down the flank, the way a battery reads at a glance."""
    c = machine_casing()
    for i, y in enumerate((3, 6, 9)):
        c.rect(4, y, 11, y + 1, FLUX[3 - i])
        c.hline(4, 11, y, FLUX[4 - i])
        c.hline(4, 11, y + 1, FLUX[1])
    c.rect(4, 12, 11, 12, CASING[0])
    c.save("surge_bank_side", BLOCK_DIR)


def surge_bank_front():
    """A charge window, full at the bottom and fading out towards the top."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[0])
    c.rect(4, 4, 11, 11, FLUX[0])
    for i, y in enumerate((10, 9, 8, 7, 6, 5)):
        shade = 4 if i < 2 else (3 if i < 4 else 2)
        c.rect(4, y, 11, y, FLUX[shade])
    c.hline(4, 11, 11, FLUX[4])
    # Terminal marks either side of the window.
    for y in (5, 10):
        c.dot(3, y, CASING[4])
        c.dot(12, y, CASING[4])
    c.save("surge_bank_front", BLOCK_DIR)


def surge_bank_top():
    """Two terminal posts and the bus bar between them."""
    c = machine_casing()
    c.rect(3, 7, 12, 8, CASING[0])
    c.hline(3, 12, 7, CASING[3])
    for x in (4, 11):
        c.rect(x - 1, 4, x + 1, 6, CASING[3])
        c.rect(x - 1, 9, x + 1, 11, CASING[3])
        c.dot(x, 5, FLUX[3])
        c.dot(x, 10, FLUX[3])
    c.save("surge_bank_top", BLOCK_DIR)


def surge_bank_bottom():
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[1])
    c.rect(5, 5, 10, 10, CASING[0])
    c.dot(7, 7, FLUX[2])
    c.dot(8, 8, FLUX[2])
    c.save("surge_bank_bottom", BLOCK_DIR)


def flux_crystal():
    """A cut gem: a bright core, facet lines, and a darker girdle so it reads at 16x."""
    c = Canvas()
    body = (
        (7, 2, 8, 2),
        (6, 3, 9, 3),
        (5, 4, 10, 5),
        (4, 6, 11, 9),
        (5, 10, 10, 11),
        (6, 12, 9, 12),
        (7, 13, 8, 13),
    )
    for x0, y0, x1, y1 in body:
        c.rect(x0, y0, x1, y1, FLUX[2])

    # Facets: a lit left shoulder, a shadowed right one, and a highlight down the middle.
    c.rect(6, 4, 7, 5, FLUX[3])
    c.rect(5, 6, 6, 9, FLUX[3])
    c.rect(9, 6, 10, 9, FLUX[1])
    c.rect(8, 10, 9, 11, FLUX[1])
    c.vline(7, 3, 12, FLUX[4])
    c.dot(8, 4, FLUX[4])
    c.dot(8, 9, FLUX[4])

    # Girdle, so the silhouette does not bleed into a bright inventory background.
    c.hline(7, 8, 13, FLUX[0])
    c.dot(4, 6, FLUX[0])
    c.dot(4, 9, FLUX[0])
    c.dot(11, 6, FLUX[0])
    c.dot(11, 9, FLUX[0])
    c.save("flux_crystal")


def flux_coupler():
    """A socketed cradle: a crystal seated in a metal clip, with a lead running off it."""
    c = Canvas()
    # The clip: a squat frame with an open top the crystal sits in.
    c.rect(3, 5, 12, 13, CASING[2])
    c.rect(4, 6, 11, 12, CASING[1])
    c.hline(3, 12, 13, CASING[0])
    c.hline(4, 11, 6, CASING[3])

    # The crystal in the socket, cut off by the rim so it reads as seated rather than floating.
    c.rect(6, 2, 9, 3, FLUX[2])
    c.rect(5, 4, 10, 8, FLUX[2])
    c.rect(6, 3, 8, 7, FLUX[3])
    c.vline(7, 3, 7, FLUX[4])
    c.rect(9, 4, 10, 8, FLUX[1])
    c.hline(5, 10, 9, FLUX[0])

    # The lead out of the bottom corner: what makes it a coupler rather than a lamp.
    c.dot(12, 11, CASING[3])
    c.dot(13, 12, CASING[3])
    c.dot(13, 13, CASING[2])
    c.save("flux_coupler")


def crystal_charger_side():
    """A crystal held in a clamp, with the coils that pour charge into it."""
    c = machine_casing()
    c.rect(4, 3, 11, 12, CASING[1])
    c.rect(6, 5, 9, 10, FLUX[2])
    c.rect(7, 6, 8, 9, FLUX[3])
    c.dot(7, 7, FLUX[4])
    for y in (5, 7, 9):
        c.dot(4, y, FLUX[3])
        c.dot(11, y, FLUX[1])
    c.hline(5, 10, 4, CASING[3])
    c.hline(5, 10, 11, CASING[0])
    c.save("crystal_charger_side", BLOCK_DIR)


def crystal_charger_front():
    """The working chamber: a crystal mid-charge between two emitter plates."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[0])
    c.rect(4, 4, 11, 11, FLUX[0])
    c.rect(6, 5, 9, 10, FLUX[1])
    c.rect(7, 6, 8, 9, FLUX[3])
    c.dot(7, 7, FLUX[4])
    for y in range(5, 11):
        c.dot(4, y, FLUX[2] if y % 2 == 0 else FLUX[1])
        c.dot(11, y, FLUX[2] if y % 2 else FLUX[1])
    c.save("crystal_charger_front", BLOCK_DIR)


def crystal_charger_top():
    """The hopper mouth crystals drop through."""
    c = machine_casing()
    c.rect(4, 4, 11, 11, CASING[1])
    c.rect(5, 5, 10, 10, CASING[0])
    c.rect(6, 6, 9, 9, FLUX[1])
    c.rect(7, 7, 8, 8, FLUX[3])
    c.hline(5, 10, 5, CASING[3])
    c.save("crystal_charger_top", BLOCK_DIR)


def crystal_charger_bottom():
    c = machine_casing()
    c.rect(4, 4, 11, 11, CASING[1])
    c.rect(6, 6, 9, 9, CASING[0])
    c.dot(7, 7, FLUX[2])
    c.dot(8, 8, FLUX[2])
    c.save("crystal_charger_bottom", BLOCK_DIR)


def resonance_crusher_side():
    """The shaker housing: heavy ribs, and the resonance rings running through them."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[1])
    for x in (4, 7, 10):
        c.vline(x, 4, 11, CASING[3])
        c.vline(x + 1, 4, 11, CASING[0])
    # The wave travelling down the flank, brightest where it is loudest.
    for x, y in ((3, 8), (4, 7), (5, 6), (6, 7), (7, 8), (8, 9), (9, 10), (10, 9), (11, 8), (12, 7)):
        c.dot(x, y, VOID[3])
    c.save("resonance_crusher_side", BLOCK_DIR)


def resonance_crusher_front():
    """The tuning dial: a scale, a needle, and the crushing chamber behind glass."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[0])
    c.rect(4, 4, 11, 11, VOID[0])
    # Scale marks across the top of the dial.
    for x in range(4, 12, 2):
        c.dot(x, 5, VOID[2])
    # The needle, leaning to where this material rings.
    for x, y in ((7, 9), (8, 8), (8, 7), (9, 6)):
        c.dot(x, y, VOID[4])
    c.dot(7, 10, NICKEL[3])
    # Ore rattling loose in the bottom of the chamber.
    c.dot(5, 10, NICKEL[2])
    c.dot(10, 10, NICKEL[1])
    c.hline(4, 11, 11, VOID[1])
    c.save("resonance_crusher_front", BLOCK_DIR)


def resonance_crusher_top():
    """The feed throat, with the hammer plate at the bottom of it."""
    c = machine_casing()
    c.rect(3, 3, 12, 12, CASING[0])
    c.rect(4, 4, 11, 11, CASING[1])
    c.rect(5, 5, 10, 10, CASING[0])
    for y in (6, 8, 10):
        c.hline(5, 10, y, NICKEL[1])
    c.hline(5, 10, 5, CASING[3])
    c.save("resonance_crusher_top", BLOCK_DIR)


def resonance_crusher_bottom():
    """The chute the dust falls out of."""
    c = machine_casing()
    c.rect(4, 4, 11, 11, CASING[1])
    c.rect(5, 6, 10, 9, CASING[0])
    c.hline(5, 10, 6, CASING[3])
    for x in (6, 9):
        c.dot(x, 8, NICKEL[2])
    c.save("resonance_crusher_bottom", BLOCK_DIR)


def thermal_probe():
    """A probe rod with a hot bulb at the tip and a readout on the grip: it reads heat, it links nothing."""
    c = Canvas()
    # Grip, lower left, wrapped dark.
    for i in range(4):
        x, y = 2 + i, 13 - i
        c.dot(x, y, VOID[2])
        c.dot(x + 1, y, VOID[3])
        c.dot(x, y - 1, VOID[1])
    # The rod up to the bulb, lit on its upper-left edge.
    for i in range(5):
        x, y = 6 + i, 9 - i
        c.dot(x, y, NICKEL[2])
        c.dot(x, y - 1, NICKEL[4])
        c.dot(x + 1, y, NICKEL[0])
    # The bulb: hot at the core, dark at the rim.
    c.rect(11, 2, 13, 4, HEAT[1])
    c.dot(12, 3, HEAT[4])
    c.dot(11, 3, HEAT[3])
    c.dot(12, 2, HEAT[3])
    c.dot(13, 4, HEAT[0])
    # The readout on the grip: two lit segments.
    c.dot(4, 9, FLUX[3])
    c.dot(5, 8, FLUX[2])
    c.save("thermal_probe")


def linking_tool():
    """A stubby wrench with a linking head: the jaw says tool, the lens says what it links."""
    c = Canvas()
    # Handle, running bottom-left to upper-right with the light on its upper-left edge.
    for i in range(6):
        x, y = 3 + i, 12 - i
        c.dot(x, y, NICKEL[1])
        c.dot(x, y - 1, NICKEL[3])
        c.dot(x + 1, y, NICKEL[0])
    # Grip wrap.
    for i in range(2):
        c.dot(3 + i, 12 - i, VOID[2])
        c.dot(4 + i, 12 - i, VOID[3])
    # Open jaw at the head.
    c.rect(9, 4, 12, 7, NICKEL[2])
    c.hline(9, 12, 4, NICKEL[4])
    c.vline(12, 4, 7, NICKEL[0])
    c.rect(10, 5, 11, 6, TRANSPARENT)
    c.dot(9, 7, NICKEL[0])
    # The linking lens, lit because that is the half a wrench does not have.
    c.dot(8, 8, FLUX[2])
    c.dot(9, 9, FLUX[3])
    c.dot(8, 9, FLUX[1])
    c.save("linking_tool")


def filter_item():
    """A punched plate: a grid of holes is the oldest picture of a filter there is."""
    c = Canvas()
    c.paint((
        "................",
        "....33333333....",
        "...3444444443...",
        "...3411411443...",
        "...3411411443...",
        "...3444444443...",
        "...3411411443...",
        "...3411411443...",
        "...3444444443...",
        "...3411411443...",
        "...3411411443...",
        "...3444444443...",
        "...2222222222...",
        "....22222222....",
        "................",
        "................",
    ), NICKEL)
    # One lit hole, so it reads as something that lets a chosen thing through.
    c.dot(5, 6, FLUX[3])
    c.dot(6, 6, FLUX[2])
    c.dot(5, 7, FLUX[2])
    c.dot(6, 7, FLUX[1])
    c.save("filter")


def machine_frame():
    """An empty casing: four bars, corner plates, and nothing inside yet."""
    c = Canvas()
    c.rect(2, 2, 13, 13, CASING[1])
    c.rect(3, 3, 12, 12, TRANSPARENT)
    c.hline(2, 13, 2, CASING[3])
    c.vline(2, 2, 13, CASING[3])
    c.hline(2, 13, 13, CASING[0])
    c.vline(13, 2, 13, CASING[0])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.dot(x, y, NICKEL[3])
    # Cross braces, so it reads as a frame rather than a picture border.
    for i in range(4):
        c.dot(4 + i, 4 + i, CASING[2])
        c.dot(11 - i, 4 + i, CASING[2])
    c.save("machine_frame")


def metal_dust(name, ramp):
    """A heap of crushed metal, lit from the upper left.

    The peak sits left of centre and the right shoulder runs out further than the left, because a
    symmetrical mound reads as a triangle rather than as something that was poured. Grains are
    placed by hand instead of scattered by rule: an even sprinkle turns into a visible lattice at
    this size, which is exactly what a powder must not look like.
    """
    metal_dust.ART = (
        "................",
        "................",
        "................",
        "................",
        "......43........",
        ".....3432.......",
        "....334221......",
        "....3322211.....",
        "...334222211....",
        "...3224222111...",
        "..33222212110...",
        "..324222211110..",
        ".1211211110100..",
        ".1.00000000.0.1.",
        "................",
        "................",
    )
    c = Canvas()
    c.paint(metal_dust.ART, ramp)
    c.save(name)


def machine_gui():
    """The one window every machine uses. Everything specific to a machine is a sprite."""
    s = Sheet()
    gui_chrome(s)
    gui_sprites(s)
    s.save("machine")


def logic_port():
    """The pad itself: a thin plate with an emitter ring, so a linked face reads at a glance.

    Full-bleed rather than an item silhouette -- this one is a block texture, and the model
    wraps it round a 2px slab, so the edges have to tile against whatever it is stuck to.
    """
    c = Canvas()

    # Plate with a bevel, lit upper left like everything else.
    c.rect(0, 0, 15, 15, CASING[0])
    c.rect(1, 1, 14, 14, CASING[2])
    c.hline(1, 14, 1, CASING[4])
    c.vline(1, 1, 14, CASING[3])
    c.hline(1, 14, 14, CASING[1])
    c.vline(14, 2, 14, CASING[1])

    # Mounting screws.
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.dot(x, y, CASING[0])

    # Emitter ring. Written out because a ring this small is drawn, not computed.
    ring = [
        "....0000....",
        "..00222200..",
        ".02233332 0.",
        ".0234..4320.",
        "0223.44.3220",
        "0234.44.4320",
        "0234.44.4320",
        "0223.44.3220",
        ".0234..4320.",
        ".02233332 0.",
        "..00222200..",
        "....0000....",
    ]
    c.paint([row.replace(" ", "0") for row in ring], FLUX, 2, 2)

    c.save("logic_port", BLOCK_DIR)


def link_range_upgrade():
    """An aerial, not a chip -- range is a port upgrade, and it should not read as a machine one."""
    c = Canvas()

    # Base the mast stands on.
    c.rect(4, 12, 11, 14, CASING[1])
    c.rect(5, 12, 10, 13, CASING[3])
    c.hline(5, 10, 12, CASING[4])
    c.hline(4, 11, 14, CASING[0])

    # Mast.
    c.vline(7, 3, 12, NICKEL[1])
    c.vline(8, 3, 12, NICKEL[3])

    # Cross elements, widening downwards.
    for y, half in ((5, 2), (7, 3), (9, 4)):
        c.hline(8 - half, 7 + half, y, NICKEL[2])
        c.dot(8 - half, y, NICKEL[0])
        c.dot(7 + half, y, NICKEL[0])

    # Signal off the tip.
    c.dot(7, 2, FLUX[4])
    c.dot(8, 2, FLUX[4])
    c.dot(5, 1, FLUX[2])
    c.dot(10, 1, FLUX[2])
    c.dot(4, 0, FLUX[1])
    c.dot(11, 0, FLUX[1])

    c.save("link_range_upgrade")


def unbound_link_card():
    """Card-shaped like the config card, void-coloured: same slot family, different rules."""
    c = Canvas()

    c.rect(3, 2, 12, 13, VOID[0])
    c.rect(4, 3, 11, 12, VOID[1])
    for x, y in ((3, 2), (12, 2), (3, 13), (12, 13)):
        c.dot(x, y, TRANSPARENT)

    c.hline(4, 11, 3, VOID[3])
    c.vline(4, 3, 12, VOID[2])
    c.hline(4, 11, 12, VOID[0])
    c.vline(11, 4, 12, VOID[0])

    # An eye in the middle: the thing that costs a network its second card.
    eye = [
        ".0000.",
        "023320",
        "13.431",
        "13.431",
        "023320",
        ".0000.",
    ]
    c.paint(eye, VOID, 5, 5)
    c.dot(7, 7, FLUX[4])
    c.dot(8, 7, FLUX[3])
    c.dot(7, 8, FLUX[3])
    c.dot(8, 8, FLUX[2])

    c.save("unbound_link_card")


def energy_injector_side():
    """Casing with a coil band: it holds a charge for a network rather than doing work."""
    c = machine_casing()
    c.rect(2, 6, 13, 9, CASING[0])
    for x in range(3, 13, 2):
        c.vline(x, 6, 9, FLUX[2])
        c.vline(x + 1, 6, 9, FLUX[0])
    c.hline(3, 12, 6, FLUX[3])
    c.save("energy_injector_side", BLOCK_DIR)


def energy_injector_front():
    """A broadcast dish: what the pads on a network are actually drawing from."""
    c = machine_casing()
    c.rect(4, 4, 11, 11, CASING[0])
    c.rect(5, 5, 10, 10, FLUX[0])

    dish = [
        "..0000..",
        ".012210.",
        "01233210",
        "01234210",
        "01234210",
        "01233210",
        ".012210.",
        "..0000..",
    ]
    c.paint(dish, FLUX, 4, 4)
    c.dot(7, 7, FLUX[4])
    c.dot(8, 8, FLUX[4])
    c.save("energy_injector_front", BLOCK_DIR)


def energy_injector_top():
    c = machine_casing()
    c.rect(5, 5, 10, 10, CASING[0])
    c.rect(6, 6, 9, 9, FLUX[1])
    c.hline(6, 9, 6, FLUX[3])
    c.dot(7, 7, FLUX[4])
    c.save("energy_injector_top", BLOCK_DIR)


def energy_injector_bottom():
    c = machine_casing()
    c.rect(4, 4, 11, 11, CASING[1])
    c.hline(4, 11, 4, CASING[2])
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.dot(x, y, CASING[3])
    c.save("energy_injector_bottom", BLOCK_DIR)


def main():
    upgrade_chip("energy_upgrade", FLUX)
    upgrade_chip("speed_upgrade", COPPER)
    upgrade_chip("overclock_upgrade", VOID)
    upgrade_chip("stack_upgrade", VERDIGRIS, stacked=True)
    tier_upgrade("iron_tier_upgrade", SILVER)
    tier_upgrade("gold_tier_upgrade", GOLD)
    tier_upgrade("diamond_tier_upgrade", FLUX)
    tier_upgrade("netherite_tier_upgrade", TRIM)
    config_card()
    linking_tool()
    thermal_probe()
    filter_item()
    machine_frame()
    metal_dust("iron_dust", SILVER)
    metal_dust("copper_dust", COPPER)
    metal_dust("gold_dust", GOLD)

    corrosion_cell_side()
    corrosion_cell_front()
    corrosion_cell_top()
    corrosion_cell_bottom()

    hydrostatic_generator_side()
    hydrostatic_generator_front()
    hydrostatic_generator_top()
    hydrostatic_generator_bottom()

    photovore_side()
    photovore_front()
    photovore_top()
    photovore_bottom()

    impact_dynamo_side()
    impact_dynamo_front()
    impact_dynamo_top()
    impact_dynamo_bottom()

    spawner_siphon_side()
    spawner_siphon_front()
    spawner_siphon_top()
    spawner_siphon_bottom()

    enchantment_combustor_side()
    enchantment_combustor_front()
    enchantment_combustor_top()
    enchantment_combustor_bottom()

    surge_bank_side()
    surge_bank_front()
    surge_bank_top()
    surge_bank_bottom()

    resonance_crusher_side()
    resonance_crusher_front()
    resonance_crusher_top()
    resonance_crusher_bottom()

    crystal_charger_side()
    crystal_charger_front()
    crystal_charger_top()
    crystal_charger_bottom()
    flux_crystal()
    flux_coupler()
    energy_injector_side()
    energy_injector_front()
    energy_injector_top()
    energy_injector_bottom()
    logic_port()
    link_range_upgrade()
    unbound_link_card()

    machine_casing_block()
    machine_casing_block(True)
    item_hatch()
    item_hatch(True)
    energy_hatch()
    energy_hatch(True)
    redstone_hatch()
    redstone_hatch(True)
    fluid_hatch()
    fluid_hatch(True)
    geothermal_casing_block()
    machine_casing_panel()
    for index in (1, 2, 3, 4):
        machine_casing_panel_detail(index)
    geothermal_casing_plate()
    geothermal_casing_ring()
    geothermal_casing_ring_corner()
    for mode in ('input', 'output', 'both'):
        hatch_marker(mode)
    annihilation_furnace_front(False)
    annihilation_furnace_front(True)
    corium_still()
    corium_flow()
    geothermal_tap_side()
    geothermal_tap_front()
    geothermal_tap_front(True)
    geothermal_tap_top()
    geothermal_tap_bottom()

    machine_gui()


if __name__ == "__main__":
    main()
