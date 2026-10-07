package com.rentnest.app.data.seed

/** Rupee amounts; converted to paise when seeded. */
internal data class ItemTemplate(
    val title: String,
    val daily: Long,
    val weekly: Long,
    val deposit: Long,
    val description: String,
    val specs: List<Pair<String, String>>,
)

internal val SEED_CATEGORIES = listOf(
    "Cameras" to "cameras", "Tools" to "tools", "Camping" to "camping", "Party & Events" to "party",
    "Sports" to "sports", "Electronics" to "electronics", "Vehicles" to "vehicles", "Music" to "music",
)

/** 5 templates per category, in SEED_CATEGORIES order (item ids 1..40). */
internal val SEED_ITEMS = listOf(
    ItemTemplate("Sony A7 IV Mirrorless Kit", 1800, 10000, 15000, "Full-frame hybrid camera ideal for weddings, travel and YouTube.", listOf("Sensor" to "33MP full-frame", "Lens" to "28–70mm f/3.5–5.6", "Video" to "4K 60p", "In the box" to "2 batteries, charger, 128GB card")),
    ItemTemplate("Canon EOS R6 Body", 1600, 9000, 15000, "Fast, low-light monster for events and sports photography.", listOf("Sensor" to "20MP full-frame", "Burst" to "20 fps", "Mount" to "Canon RF")),
    ItemTemplate("GoPro HERO12 Black", 600, 3200, 5000, "Waterproof action camera with mounts for helmets, bikes and chest.", listOf("Video" to "5.3K 60p", "Waterproof" to "10 m", "Includes" to "3 mounts, 2 batteries")),
    ItemTemplate("DJI Mini 4 Pro Drone", 1500, 8500, 20000, "Under-249g drone with obstacle sensing and 4K HDR video.", listOf("Flight time" to "34 min", "Range" to "20 km", "Includes" to "3 batteries, RC 2 controller")),
    ItemTemplate("Godox Studio Light Kit", 900, 5000, 6000, "Two-light softbox setup for portraits and product shoots.", listOf("Power" to "2 × 300W", "Modifiers" to "2 softboxes", "Stands" to "2 × 2.8 m")),
    ItemTemplate("Bosch Rotary Hammer Drill", 450, 2400, 3000, "Heavy-duty SDS drill for concrete and masonry.", listOf("Power" to "800W", "Impact" to "2.7 J", "Bits" to "5-piece SDS set")),
    ItemTemplate("Makita Cordless Drill Set", 350, 1800, 2500, "Lightweight drill-driver with two batteries for home projects.", listOf("Voltage" to "18V", "Torque" to "62 Nm", "Includes" to "2 batteries, 30-bit set")),
    ItemTemplate("Karcher Pressure Washer K3", 500, 2700, 4000, "Blast dirt off cars, patios and bikes in minutes.", listOf("Pressure" to "120 bar", "Hose" to "6 m", "Accessories" to "Vario & dirt-blaster lance")),
    ItemTemplate("Aluminium Extension Ladder 24ft", 300, 1500, 2000, "Sturdy two-section ladder for painting and repairs.", listOf("Max height" to "24 ft", "Load" to "150 kg", "Weight" to "13 kg")),
    ItemTemplate("Tile Cutter 600mm", 400, 2100, 3000, "Precise manual cutter for ceramic and porcelain tiles.", listOf("Cut length" to "600 mm", "Thickness" to "up to 12 mm")),
    ItemTemplate("4-Person Dome Tent", 400, 2200, 3000, "Weather-proof tent with vestibule, sets up in 10 minutes.", listOf("Sleeps" to "4", "Waterproof" to "3000 mm", "Weight" to "4.8 kg")),
    ItemTemplate("Sleeping Bag (-5°C)", 150, 800, 1000, "Mummy bag rated for cold Himalayan nights. Washed after every rental.", listOf("Comfort" to "-5°C", "Fill" to "Hollow fibre", "Packed size" to "40 × 25 cm")),
    ItemTemplate("Camping Stove & Cookset", 200, 1000, 1500, "Compact gas stove with pots, pan and cutlery for two.", listOf("Fuel" to "Butane canister", "Includes" to "2 pots, pan, mugs")),
    ItemTemplate("Trekking Backpack 60L", 180, 950, 1500, "Adjustable frame pack with rain cover for multi-day treks.", listOf("Capacity" to "60 L", "Rain cover" to "Included", "Weight" to "1.9 kg")),
    ItemTemplate("Portable Power Station 500W", 700, 3800, 8000, "Silent battery generator for campsites, shoots and power cuts.", listOf("Capacity" to "518 Wh", "Outputs" to "2 AC, 4 USB, car port", "Solar" to "Supported")),
    ItemTemplate("JBL PartyBox 310 Speaker", 1200, 6500, 8000, "Big-sound party speaker with light show and mic input.", listOf("Power" to "240W", "Battery" to "18 hours", "Inputs" to "Mic, guitar, Bluetooth")),
    ItemTemplate("LED Par Light Set (8)", 900, 4800, 5000, "DMX-ready uplighting to transform any venue.", listOf("Lights" to "8 × RGBW", "Control" to "DMX + remote")),
    ItemTemplate("Fog Machine 1200W", 500, 2600, 3000, "Thick fog for dance floors and stage entries. Fluid included.", listOf("Output" to "10,000 cfm", "Fluid" to "1 L included")),
    ItemTemplate("Popcorn Machine", 600, 3200, 4000, "Retro cart-style popcorn maker for birthdays and movie nights.", listOf("Kettle" to "8 oz", "Output" to "~100 servings/hr")),
    ItemTemplate("Wooden Folding Chairs (10)", 800, 4200, 5000, "Elegant chairs for weddings, poojas and garden parties.", listOf("Quantity" to "10", "Finish" to "Natural teak")),
    ItemTemplate("Road Bike — Giant TCR", 900, 5000, 12000, "Lightweight carbon road bike, fitted to your height on pickup.", listOf("Frame" to "Carbon, size M", "Groupset" to "Shimano 105", "Includes" to "Helmet, lock")),
    ItemTemplate("Kayak with Paddle", 1100, 6000, 10000, "Stable sit-on-top kayak for lakes and calm rivers.", listOf("Seats" to "1", "Length" to "3 m", "Includes" to "Paddle, life jacket")),
    ItemTemplate("Badminton Pro Set", 150, 800, 1000, "Four Yonex rackets, shuttles and a portable net.", listOf("Rackets" to "4", "Net" to "Portable, 5 m")),
    ItemTemplate("Ski & Snowboard Kit", 1300, 7000, 12000, "Everything for Gulmarg: board or skis, boots and goggles.", listOf("Boot sizes" to "UK 6–11", "Includes" to "Helmet, goggles")),
    ItemTemplate("Golf Club Set (Full)", 1000, 5500, 10000, "Complete Callaway set with stand bag.", listOf("Clubs" to "12 + putter", "Hand" to "Right")),
    ItemTemplate("Epson 4K Projector + Screen", 1400, 7500, 12000, "Home-theatre projector with a 120\" tripod screen.", listOf("Resolution" to "4K PRO-UHD", "Brightness" to "3000 lumens", "Screen" to "120\" tripod")),
    ItemTemplate("MacBook Pro 14\" M3", 2200, 12000, 40000, "Pro laptop for editing gigs and conferences. Wiped after each rental.", listOf("Chip" to "Apple M3 Pro", "Memory" to "18 GB", "Storage" to "512 GB")),
    ItemTemplate("PlayStation 5 + 2 Controllers", 900, 4800, 10000, "Console with two controllers and five popular games.", listOf("Storage" to "825 GB", "Games" to "5 included")),
    ItemTemplate("iPad Pro 12.9\" with Pencil", 1200, 6500, 25000, "Big-screen tablet for design, notes and presentations.", listOf("Storage" to "256 GB", "Accessories" to "Apple Pencil 2, folio")),
    ItemTemplate("Meta Quest 3 VR Headset", 800, 4300, 12000, "Mixed-reality headset for parties and demos.", listOf("Storage" to "128 GB", "Includes" to "2 controllers, charging dock")),
    ItemTemplate("Royal Enfield Classic 350", 1500, 8500, 20000, "Iconic cruiser for weekend road trips. Helmet included.", listOf("Engine" to "349 cc", "Fuel" to "Full-to-full", "Licence" to "Required")),
    ItemTemplate("Electric Scooter Ola S1", 700, 3800, 8000, "Zippy EV scooter for city errands. Charger included.", listOf("Range" to "~150 km", "Top speed" to "90 km/h")),
    ItemTemplate("Thule Roof Box 400L", 500, 2600, 5000, "Extra luggage space for family road trips.", listOf("Capacity" to "400 L", "Fits" to "Most roof bars")),
    ItemTemplate("Mountain Bike — Trek Marlin", 600, 3200, 8000, "Hardtail MTB for trails around Nandi Hills.", listOf("Frame" to "Alloy, size L", "Gears" to "1×10")),
    ItemTemplate("Car Bike Rack (2 bikes)", 300, 1600, 3000, "Hitch-free trunk rack that carries two bikes.", listOf("Capacity" to "2 bikes", "Fits" to "Sedans & hatchbacks")),
    ItemTemplate("Yamaha Digital Piano P-145", 900, 4800, 10000, "88 weighted keys with stand and sustain pedal.", listOf("Keys" to "88 weighted", "Includes" to "Stand, pedal, headphones")),
    ItemTemplate("Fender Stratocaster + Amp", 800, 4300, 10000, "Classic Strat with a 40W amp, cable and gig bag.", listOf("Guitar" to "Player Series", "Amp" to "Fender Frontman 40W")),
    ItemTemplate("Roland Electronic Drum Kit", 1200, 6500, 12000, "Mesh-head kit, quiet enough for apartments.", listOf("Pads" to "5 mesh + 3 cymbals", "Includes" to "Throne, sticks")),
    ItemTemplate("Shure SM58 Mic Pack (4)", 600, 3200, 5000, "Four legendary vocal mics with stands and cables.", listOf("Mics" to "4 × SM58", "Includes" to "Stands, XLR cables")),
    ItemTemplate("Pioneer DJ Controller DDJ-FLX4", 1000, 5400, 8000, "Two-deck controller that works with rekordbox and Serato.", listOf("Decks" to "2", "Includes" to "Headphones, laptop stand")),
)

internal val REVIEW_TEXTS = listOf(
    "Spotless condition and pickup took two minutes. Will rent again!",
    "Exactly as described. The provider explained everything patiently.",
    "Saved me from buying one for a single weekend. Great value.",
    "Worked flawlessly for our shoot. Batteries were fully charged.",
    "Slight wear but fully functional. Friendly and flexible on timing.",
    "Super smooth booking and the deposit came back the same day.",
    "Our guests loved it — made the party!",
    "Good gear, fair price. Return was quick and hassle-free.",
)
