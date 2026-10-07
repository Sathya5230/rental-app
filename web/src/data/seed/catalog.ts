/** Rupee amounts; converted to paise when seeded. */
export interface ItemTemplate { title: string; daily: number; weekly: number; deposit: number; description: string; specs: [string, string][] }

export const SEED_CATEGORIES: [name: string, key: string][] = [
  ['Cameras', 'cameras'], ['Tools', 'tools'], ['Camping', 'camping'], ['Party & Events', 'party'],
  ['Sports', 'sports'], ['Electronics', 'electronics'], ['Vehicles', 'vehicles'], ['Music', 'music'],
];

const t = (title: string, daily: number, weekly: number, deposit: number, description: string, specs: [string, string][]): ItemTemplate =>
  ({ title, daily, weekly, deposit, description, specs });

/** 5 templates per category, in SEED_CATEGORIES order (item ids 1..40). */
export const SEED_ITEMS: ItemTemplate[] = [
  t('Sony A7 IV Mirrorless Kit', 1800, 10000, 15000, 'Full-frame hybrid camera ideal for weddings, travel and YouTube.', [['Sensor', '33MP full-frame'], ['Lens', '28–70mm f/3.5–5.6'], ['Video', '4K 60p'], ['In the box', '2 batteries, charger, 128GB card']]),
  t('Canon EOS R6 Body', 1600, 9000, 15000, 'Fast, low-light monster for events and sports photography.', [['Sensor', '20MP full-frame'], ['Burst', '20 fps'], ['Mount', 'Canon RF']]),
  t('GoPro HERO12 Black', 600, 3200, 5000, 'Waterproof action camera with mounts for helmets, bikes and chest.', [['Video', '5.3K 60p'], ['Waterproof', '10 m'], ['Includes', '3 mounts, 2 batteries']]),
  t('DJI Mini 4 Pro Drone', 1500, 8500, 20000, 'Under-249g drone with obstacle sensing and 4K HDR video.', [['Flight time', '34 min'], ['Range', '20 km'], ['Includes', '3 batteries, RC 2 controller']]),
  t('Godox Studio Light Kit', 900, 5000, 6000, 'Two-light softbox setup for portraits and product shoots.', [['Power', '2 × 300W'], ['Modifiers', '2 softboxes'], ['Stands', '2 × 2.8 m']]),
  t('Bosch Rotary Hammer Drill', 450, 2400, 3000, 'Heavy-duty SDS drill for concrete and masonry.', [['Power', '800W'], ['Impact', '2.7 J'], ['Bits', '5-piece SDS set']]),
  t('Makita Cordless Drill Set', 350, 1800, 2500, 'Lightweight drill-driver with two batteries for home projects.', [['Voltage', '18V'], ['Torque', '62 Nm'], ['Includes', '2 batteries, 30-bit set']]),
  t('Karcher Pressure Washer K3', 500, 2700, 4000, 'Blast dirt off cars, patios and bikes in minutes.', [['Pressure', '120 bar'], ['Hose', '6 m'], ['Accessories', 'Vario & dirt-blaster lance']]),
  t('Aluminium Extension Ladder 24ft', 300, 1500, 2000, 'Sturdy two-section ladder for painting and repairs.', [['Max height', '24 ft'], ['Load', '150 kg'], ['Weight', '13 kg']]),
  t('Tile Cutter 600mm', 400, 2100, 3000, 'Precise manual cutter for ceramic and porcelain tiles.', [['Cut length', '600 mm'], ['Thickness', 'up to 12 mm']]),
  t('4-Person Dome Tent', 400, 2200, 3000, 'Weather-proof tent with vestibule, sets up in 10 minutes.', [['Sleeps', '4'], ['Waterproof', '3000 mm'], ['Weight', '4.8 kg']]),
  t('Sleeping Bag (-5°C)', 150, 800, 1000, 'Mummy bag rated for cold Himalayan nights. Washed after every rental.', [['Comfort', '-5°C'], ['Fill', 'Hollow fibre'], ['Packed size', '40 × 25 cm']]),
  t('Camping Stove & Cookset', 200, 1000, 1500, 'Compact gas stove with pots, pan and cutlery for two.', [['Fuel', 'Butane canister'], ['Includes', '2 pots, pan, mugs']]),
  t('Trekking Backpack 60L', 180, 950, 1500, 'Adjustable frame pack with rain cover for multi-day treks.', [['Capacity', '60 L'], ['Rain cover', 'Included'], ['Weight', '1.9 kg']]),
  t('Portable Power Station 500W', 700, 3800, 8000, 'Silent battery generator for campsites, shoots and power cuts.', [['Capacity', '518 Wh'], ['Outputs', '2 AC, 4 USB, car port'], ['Solar', 'Supported']]),
  t('JBL PartyBox 310 Speaker', 1200, 6500, 8000, 'Big-sound party speaker with light show and mic input.', [['Power', '240W'], ['Battery', '18 hours'], ['Inputs', 'Mic, guitar, Bluetooth']]),
  t('LED Par Light Set (8)', 900, 4800, 5000, 'DMX-ready uplighting to transform any venue.', [['Lights', '8 × RGBW'], ['Control', 'DMX + remote']]),
  t('Fog Machine 1200W', 500, 2600, 3000, 'Thick fog for dance floors and stage entries. Fluid included.', [['Output', '10,000 cfm'], ['Fluid', '1 L included']]),
  t('Popcorn Machine', 600, 3200, 4000, 'Retro cart-style popcorn maker for birthdays and movie nights.', [['Kettle', '8 oz'], ['Output', '~100 servings/hr']]),
  t('Wooden Folding Chairs (10)', 800, 4200, 5000, 'Elegant chairs for weddings, poojas and garden parties.', [['Quantity', '10'], ['Finish', 'Natural teak']]),
  t('Road Bike — Giant TCR', 900, 5000, 12000, 'Lightweight carbon road bike, fitted to your height on pickup.', [['Frame', 'Carbon, size M'], ['Groupset', 'Shimano 105'], ['Includes', 'Helmet, lock']]),
  t('Kayak with Paddle', 1100, 6000, 10000, 'Stable sit-on-top kayak for lakes and calm rivers.', [['Seats', '1'], ['Length', '3 m'], ['Includes', 'Paddle, life jacket']]),
  t('Badminton Pro Set', 150, 800, 1000, 'Four Yonex rackets, shuttles and a portable net.', [['Rackets', '4'], ['Net', 'Portable, 5 m']]),
  t('Ski & Snowboard Kit', 1300, 7000, 12000, 'Everything for Gulmarg: board or skis, boots and goggles.', [['Boot sizes', 'UK 6–11'], ['Includes', 'Helmet, goggles']]),
  t('Golf Club Set (Full)', 1000, 5500, 10000, 'Complete Callaway set with stand bag.', [['Clubs', '12 + putter'], ['Hand', 'Right']]),
  t('Epson 4K Projector + Screen', 1400, 7500, 12000, 'Home-theatre projector with a 120" tripod screen.', [['Resolution', '4K PRO-UHD'], ['Brightness', '3000 lumens'], ['Screen', '120" tripod']]),
  t('MacBook Pro 14" M3', 2200, 12000, 40000, 'Pro laptop for editing gigs and conferences. Wiped after each rental.', [['Chip', 'Apple M3 Pro'], ['Memory', '18 GB'], ['Storage', '512 GB']]),
  t('PlayStation 5 + 2 Controllers', 900, 4800, 10000, 'Console with two controllers and five popular games.', [['Storage', '825 GB'], ['Games', '5 included']]),
  t('iPad Pro 12.9" with Pencil', 1200, 6500, 25000, 'Big-screen tablet for design, notes and presentations.', [['Storage', '256 GB'], ['Accessories', 'Apple Pencil 2, folio']]),
  t('Meta Quest 3 VR Headset', 800, 4300, 12000, 'Mixed-reality headset for parties and demos.', [['Storage', '128 GB'], ['Includes', '2 controllers, charging dock']]),
  t('Royal Enfield Classic 350', 1500, 8500, 20000, 'Iconic cruiser for weekend road trips. Helmet included.', [['Engine', '349 cc'], ['Fuel', 'Full-to-full'], ['Licence', 'Required']]),
  t('Electric Scooter Ola S1', 700, 3800, 8000, 'Zippy EV scooter for city errands. Charger included.', [['Range', '~150 km'], ['Top speed', '90 km/h']]),
  t('Thule Roof Box 400L', 500, 2600, 5000, 'Extra luggage space for family road trips.', [['Capacity', '400 L'], ['Fits', 'Most roof bars']]),
  t('Mountain Bike — Trek Marlin', 600, 3200, 8000, 'Hardtail MTB for trails around Nandi Hills.', [['Frame', 'Alloy, size L'], ['Gears', '1×10']]),
  t('Car Bike Rack (2 bikes)', 300, 1600, 3000, 'Hitch-free trunk rack that carries two bikes.', [['Capacity', '2 bikes'], ['Fits', 'Sedans & hatchbacks']]),
  t('Yamaha Digital Piano P-145', 900, 4800, 10000, '88 weighted keys with stand and sustain pedal.', [['Keys', '88 weighted'], ['Includes', 'Stand, pedal, headphones']]),
  t('Fender Stratocaster + Amp', 800, 4300, 10000, 'Classic Strat with a 40W amp, cable and gig bag.', [['Guitar', 'Player Series'], ['Amp', 'Fender Frontman 40W']]),
  t('Roland Electronic Drum Kit', 1200, 6500, 12000, 'Mesh-head kit, quiet enough for apartments.', [['Pads', '5 mesh + 3 cymbals'], ['Includes', 'Throne, sticks']]),
  t('Shure SM58 Mic Pack (4)', 600, 3200, 5000, 'Four legendary vocal mics with stands and cables.', [['Mics', '4 × SM58'], ['Includes', 'Stands, XLR cables']]),
  t('Pioneer DJ Controller DDJ-FLX4', 1000, 5400, 8000, 'Two-deck controller that works with rekordbox and Serato.', [['Decks', '2'], ['Includes', 'Headphones, laptop stand']]),
];

export const REVIEW_TEXTS = [
  'Spotless condition and pickup took two minutes. Will rent again!',
  'Exactly as described. The provider explained everything patiently.',
  'Saved me from buying one for a single weekend. Great value.',
  'Worked flawlessly for our shoot. Batteries were fully charged.',
  'Slight wear but fully functional. Friendly and flexible on timing.',
  'Super smooth booking and the deposit came back the same day.',
  'Our guests loved it — made the party!',
  'Good gear, fair price. Return was quick and hassle-free.',
];
